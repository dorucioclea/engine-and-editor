// @flow

import * as React from 'react'
import cx from 'classnames'

import AuthPanel, { styles as authPanelStyles } from '../../shared/AuthPanel'
import Input from '../../shared/Input'
import Actions from '../../shared/Actions'
import Button from '../../shared/Button'
import AuthStep from '../../shared/AuthStep'

import withAuthFlow from '../../shared/withAuthFlow'
import { onInputChange } from '../../shared/utils'
import schemas from '../../schemas/forgotPassword'
import type { AuthFlowProps } from '../../shared/types'
import createLink from '../../../../utils/createLink'
import { post } from '../../shared/utils'

type Props = AuthFlowProps & {
    form: {
        email: string,
    },
}

class ForgotPasswordPage extends React.Component<Props> {
    submit = () => {
        const url = createLink('auth/forgotPassword')
        const { email: username } = this.props.form

        return post(url, {
            username,
        }, false, false)
    }

    onFailure = (error: Error) => {
        const { setFieldError } = this.props
        setFieldError('email', error.message)
    }

    render() {
        const { setIsProcessing, isProcessing, step, form, errors, setFieldError, next, prev, setFormField } = this.props
        return (
            <AuthPanel
                currentStep={step}
                form={form}
                onPrev={prev}
                onNext={next}
                setIsProcessing={setIsProcessing}
                validationSchemas={schemas}
                onValidationError={setFieldError}
            >
                <AuthStep
                    title="Get a link to reset your password"
                    onSubmit={this.submit}
                    onFailure={this.onFailure}
                >
                    <Input
                        name="email"
                        label="Email"
                        value={form.email}
                        onChange={onInputChange(setFormField)}
                        error={errors.email}
                        processing={step === 0 && isProcessing}
                        autoComplete="email"
                        autoFocus
                    />
                    <Actions>
                        <Button disabled={isProcessing}>Send</Button>
                    </Actions>
                </AuthStep>
                <AuthStep title="Link sent">
                    <p className={cx(authPanelStyles.spaceLarge, 'text-center')}>
                        If a user with that email exists, we have sent a link to reset the password.
                        Please check your email and click the link — it may be in your spam folder!
                    </p>
                </AuthStep>
            </AuthPanel>
        )
    }
}

export default withAuthFlow(ForgotPasswordPage, 0, {
    email: '',
})
