package com.unifina.controller.security

import grails.util.Environment
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.annotation.Secured
import grails.plugin.springsecurity.authentication.dao.NullSaltSource
import grails.plugin.springsecurity.ui.RegistrationCode

import org.apache.log4j.Logger

import com.unifina.domain.data.Feed
import com.unifina.domain.data.FeedUser
import com.unifina.domain.security.SecUser
import com.unifina.domain.security.SignupInvite
import com.unifina.domain.signalpath.ModulePackage
import com.unifina.domain.signalpath.ModulePackageUser

class RegisterController extends grails.plugin.springsecurity.ui.RegisterController {

    def mailService
    def unifinaSecurityService
    def springSecurityService
    def signupCodeService

    def log = Logger.getLogger(RegisterController)

    // override to add GET method
    static allowedMethods = [register: ['GET', 'POST']]
	
    def index() {
        render status: 404
    }

    def signup(EmailCommand cmd) {
        if (request.method != "POST") {
            return [ user: new EmailCommand() ]
        }

        if (cmd.hasErrors()) {
            render view: 'signup', model: [ user: cmd ]
            return
        }
        SignupInvite invite
        if(Environment.current == Environment.TEST){
            invite = new SignupInvite(
                    username: cmd.username,
                    code: cmd.username.replaceAll("@", "_"),
                    sent: true,
                    used: false
            )
            invite.save()
        } else {
            invite = signupCodeService.create(cmd.username)
        }
		
        mailService.sendMail {
            from grailsApplication.config.unifina.email.sender
            to invite.username
            subject grailsApplication.config.unifina.email.signup.subject
            html g.render(template:"email_signup", model:[user: invite], plugin:'unifina-core')
        }
		
        log.info("Signed up $invite.username")

        render view: 'signup', model: [ signupOk: true ]
    }

    @Secured(["ROLE_ADMIN"])
    def list() {
        [invites: SignupInvite.findAllByUsed(false)]
    }

    @Secured(["ROLE_ADMIN"])
    def sendInvite() {
        def invite = SignupInvite.findByCode(params.code)
        if (!invite) {
            flash.message = 'Invitation code not found'
            redirect action: 'list'
            return
        }

        invite.sent = true
        invite.save()

        mailService.sendMail {
            from grailsApplication.config.unifina.email.sender
            to invite.username
            subject grailsApplication.config.unifina.email.invite.subject
            html g.render(template:"email_invite", model:[user: invite], plugin:'unifina-core')
        }
		
        flash.message = "Invitation was sent to $invite.username"
        log.info("Invitation sent to $invite.username")

        redirect action: 'list'
    }

    def register(RegisterCommand cmd) {
        def conf = SpringSecurityUtils.securityConfig
        String defaultTargetUrl = conf.successHandler.defaultTargetUrl

        def invite = SignupInvite.findByCode(cmd.invite)
        if (!invite || invite.used || !invite.sent) {
            flash.message = "Sorry, that is not a valid invitation code"
            if(invite)
                flash.message+=". Code: $invite.code"
            redirect action: 'signup'
            return
        }

        log.info('Activated invite for '+invite.username+', '+invite.code)

        if (request.method == "GET") {
            // activated the registration, but not yet submitted it
            return render(view: 'register', model: [user: [username: invite.username], invite: invite.code])
        }

        cmd.username = invite.username

        if (cmd.hasErrors()) {
            log.warn("Registration command has errors: "+cmd.errors)
            return render(view: 'register', model: [user: cmd, invite: invite.code])
        }
		
        ClassLoader cl = this.getClass().getClassLoader()
        SecUser user = cl.loadClass(grailsApplication.config.grails.plugin.springsecurity.userLookup.userDomainClassName).newInstance()
		
        user.properties = cmd.properties
        user.name = cmd.name // not copied by above line for some reason
        user.password = springSecurityService.encodePassword(user.password)
        user.enabled = true
        user.accountLocked = false

        if (!user.validate()) {
            log.warn("Registration user validation failed: "+user.errors)
            return render(view: 'register', model: [ user: user, invite: invite.code])
        }

        SignupInvite.withTransaction { status ->
            if (!user.save(flush: true)) {
                log.warn("Failed to save user data: "+user.errors)
                flash.message = "Failed to save user"
                return render(view: 'register', model: [ user: user, invite: invite.code ])
            }

            invite.used = true
            if (!invite.save(flush: true)) {
                log.warn("Failed to save invite: "+invite.errors)
                flash.message = "Failed to save invite"
                return render(view: 'register', model: [ user: user, invite: invite.code ])
            }

            log.info("Created user for "+user.username)

            def UserRole = lookupUserRoleClass()
            def Role = lookupRoleClass()
            for (roleName in conf.ui.register.defaultRoleNames) {
                UserRole.create user, Role.findByAuthority(roleName)
            }

            grailsApplication.config.streamr.user.defaultFeeds.each { id ->
                new FeedUser(user: user, feed: Feed.load(id)).save(flush: true)
            }

            grailsApplication.config.streamr.user.defaultModulePackages.each { id ->
                new ModulePackageUser(user: user, modulePackage: ModulePackage.load(id)).save(flush: true)
            }
			
            mailService.sendMail {
                from grailsApplication.config.unifina.email.sender
                to user.username
                subject grailsApplication.config.unifina.email.welcome.subject
                html g.render(template:"email_welcome", model:[user: user], plugin:'unifina-core')
            }
			
            log.info("Logging in "+user.username+" after registering")
            springSecurityService.reauthenticate(user.username)
			
            flash.message = "Account created!"
            redirect uri: conf.ui.register.postRegisterUrl ?: defaultTargetUrl
        }
    }
	
    def forgotPassword(EmailCommand cmd) {
        if (request.method != 'POST') {
            return
        }

        if (!cmd.validate()) {
            render view: 'forgotPassword', model: [ user: cmd ]
            return 
        }

        def user = SecUser.findWhere(username: cmd.username)
        if (!user) {
            return [emailSent: true] // don't reveal users
        }

        def registrationCode = new RegistrationCode(username: user.username)
        registrationCode.save(flush: true)

        String url = generateLink('resetPassword', [t: registrationCode.token])

        def conf = SpringSecurityUtils.securityConfig
        def body = conf.ui.forgotPassword.emailBody
        if (body.contains('$')) {
            body = evaluate(body, [user: user, url: url])
        }

        mailService.sendMail {
            from grailsApplication.config.unifina.email.sender
            to user.username
            subject conf.ui.forgotPassword.emailSubject
            html body.toString()
        }

        [emailSent: true]
    }
	
    def resetPassword(ResetPasswordCommand command) {

        String token = params.t

        def registrationCode = token ? RegistrationCode.findByToken(token) : null
		
        if (!registrationCode) {
            flash.error = message(code: 'spring.security.ui.resetPassword.badCode')
            redirect uri: SpringSecurityUtils.securityConfig.successHandler.defaultTargetUrl
            return
        }
		
        def user = SecUser.findByUsername(registrationCode.username)
        if (!user)
        throw new RuntimeException("User belonging to the registration code was not found: $registrationCode.username")
				
        if (!request.post) {
            log.info("Password reset code activated for user $registrationCode.username")
            return [token: token, command: new ResetPasswordCommand(), user:user]
        }

        command.username = registrationCode.username
        command.validate()

        if (command.hasErrors()) {
            return [token: token, command: command, user:user]
        }

        String salt = saltSource instanceof NullSaltSource ? null : registrationCode.username
        RegistrationCode.withTransaction { status ->
            user.password = springSecurityUiService.encodePassword(command.password, salt)
            user.save()
            registrationCode.delete()
        }

        springSecurityService.reauthenticate registrationCode.username

        flash.message = message(code: 'spring.security.ui.resetPassword.success')

        def conf = SpringSecurityUtils.securityConfig
        String postResetUrl = conf.ui.register.postResetUrl ?: conf.successHandler.defaultTargetUrl
        redirect uri: postResetUrl
    }
	
    def terms() {
        render(template:"terms_and_conditions", plugin:'unifina-core')
    }
	
    def privacy() {
        render(template:"privacy_policy", plugin:'unifina-core')
    }

}

class EmailCommand {
    String username
    static constraints = {
        username blank: false, email: true
    }
}

class RegisterCommand {
    String invite
    String username
    String name
    String password
    String password2
    String timezone
    String tosConfirmed
    Integer pwdStrength
	
    def unifinaSecurityService

    static constraints = {
        importFrom SecUser

        invite blank: false

        tosConfirmed blank: false, validator: { val ->
            println('tosConfirmed '+ val)
            val == 'on'
        }

        timezone blank: false
        name blank: false
				
        password validator: {String password, RegisterCommand command ->
            return command.unifinaSecurityService.passwordValidator(password, command)
        }
        password2 validator: {value, RegisterCommand command ->
            return command.unifinaSecurityService.password2Validator(value, command)
        }

    }
}

class ResetPasswordCommand {
    String username
    String password
    String password2
    Integer pwdStrength

    def unifinaSecurityService
	
    static constraints = {
        password validator: {String password, ResetPasswordCommand command ->
            return command.unifinaSecurityService.passwordValidator(password, command)
        }
        password2 validator: {value, ResetPasswordCommand command ->
            return command.unifinaSecurityService.password2Validator(value, command)
        }
    }
}
