package com.yupi.springbootinit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import javax.sql.DataSource;

/** MySQL is the learning ledger. Neo4j's auto-configured manager cannot roll back JDBC writes. */
@Configuration
public class LearningTransactionConfig {
    @Bean(name={"learningSqlTransactionManager","transactionManager"})
    @Primary
    public DataSourceTransactionManager learningSqlTransactionManager(DataSource dataSource){return new DataSourceTransactionManager(dataSource);}
}
