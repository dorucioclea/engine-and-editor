package core

import com.unifina.domain.IntegrationKey
import com.unifina.domain.SignupMethod
import com.unifina.utils.IdGenerator
import com.unifina.utils.AlphanumericStringGenerator

databaseChangeLog = {
	changeSet(author: "teogeb", id: "test-user-for-ens-1", context: "test") {
		grailsChange {
			change {
				String account_address = '0x4178babe9e5148c6d5fd431cd72884b07ad855a0'
				def userInsertResult = sql.executeInsert("""
					INSERT INTO
						user (version, account_expired, account_locked, enabled, name, password, password_expired, username, signup_method)
					VALUES (
						0,
						false,
						false,
						true,
						'ENS Test User',
						?,
						false,
						?,
						?
					)
				""", [AlphanumericStringGenerator.getRandomAlphanumericString(32), account_address, SignupMethod.UNKNOWN.name()])
				long userId = userInsertResult[0][0]
				sql.execute("""
					INSERT INTO
						integration_key (id, version, name, json, user_id, service, id_in_service, date_created, last_updated)
					VALUES (
						?,
						0,
						'',
						CONCAT(CONCAT('{\"address\":\"',?),'\"}'),
						?,
						?,
						?,
						CURRENT_TIMESTAMP(),
						CURRENT_TIMESTAMP()
					)
				""", ['ens-test-user-id', account_address, userId, IntegrationKey.Service.ETHEREUM_ID.name(), account_address])
			}
		}
	}
}
