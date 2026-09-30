package com.agentplatform.hub.feishu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class FeishuBotSchemaMigrator implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(FeishuBotSchemaMigrator.class);

	private final JdbcTemplate jdbc;

	public FeishuBotSchemaMigrator(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void run(ApplicationArguments args) {
		Integer required = jdbc.queryForObject(
				"""
				select count(*)
				from information_schema.columns
				where table_schema = database()
				  and lower(table_name) = 'feishu_bots'
				  and lower(column_name) = 'verification_token_cipher'
				  and is_nullable = 'NO'
				""",
				Integer.class);
		if (required != null && required > 0) {
			jdbc.execute("alter table feishu_bots modify column verification_token_cipher varchar(2048) null");
			log.info("feishu_bots.verification_token_cipher is now optional");
		}
	}

}
