package com.slotmind.config;

import com.slotmind.application.WaitlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Component
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final DataSource dataSource;
    private final WaitlistService waitlist;

    public SeedRunner(DataSource dataSource, WaitlistService waitlist) {
        this.dataSource = dataSource;
        this.waitlist = waitlist;
    }

    /** Loads the desk seed data when the database is empty, then seeds the waitlist. */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            int providers;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("select count(*) from providers")) {
                result.next();
                providers = result.getInt(1);
            }
            if (providers == 0) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("data.sql"));
                log.info("Seeded the desk database");
            } else {
                log.info("Desk database already has {} providers", providers);
            }
        }
        waitlist.seedIfEmpty();
    }
}
