package com.unifina.domain

import java.util.regex.Matcher
import java.util.regex.Pattern
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
class CustomStreamIDValidator {

	interface DomainValidator {
		boolean isOwnedBy(String domain, User owner)
	}

	public static final String SANDBOX_HOST = "sandbox"
	public static final Pattern REGEX = Pattern.compile("^((?:[\\w-]+\\.?)*\\w)/(?:[\\w\\.-]+/?)*\\w\$")

	DomainValidator domainValidator

	CustomStreamIDValidator(DomainValidator domainValidator) {
		this.domainValidator = domainValidator;
	}

	boolean validate(String id, User creator) {
		if (id == null) {
			return true
		} else {
			Matcher matcher = REGEX.matcher(id)
			if (matcher.matches()) {
				String host = matcher.group(1)
				return isValidHost(host, creator)
			} else {
				return false;
			}
		}
	}

	boolean isValidHost(String host, User creator) {
		if (host.equals(SANDBOX_HOST)) {
			return true
		} else {
			return domainValidator.isOwnedBy(host, creator)
		}
	}
}
