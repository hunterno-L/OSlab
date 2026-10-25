package com.yupi.springbootinit.course;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.springframework.boot.test.context.SpringBootTest;
import javax.annotation.Resource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CourseLegacyBindingIntegrationTest {
    @Resource Driver driver;
    @Resource CourseGraphSyncService sync;

    @Test void bindingAfterProjectionKeepsLegacyIdentityAndBothSetsOfEdges() {
        String code="os.test.binding."+UUID.randomUUID().toString().replace("-","");
        String name="binding-"+code;
        Map<String,Object> parameters=Map.of("code",code,"name",name);
        try(Session session=driver.session();Transaction tx=session.beginTransaction()) {
            long legacy=tx.run("CREATE (n:Entity {name:$name}) RETURN id(n) AS id",parameters).single().get("id").asLong();
            tx.run("CREATE (p:Entity {nodeCode:$code,name:$name,displayName:$name,homeSectionCode:'sec.overview',courseProjection:true}),(m:Entity {name:$code}) WITH p,m MATCH (n:Entity {name:$name}) WHERE n.nodeCode IS NULL CREATE (n)-[:related]->(m),(p)-[:REQUIRES]->(m)",parameters).consume();
            sync.bindProjection(tx,code,name);
            assertEquals(1,tx.run("MATCH (n:Entity {nodeCode:$code}) RETURN count(n) AS total",parameters).single().get("total").asInt());
            assertEquals(legacy,tx.run("MATCH (n:Entity {nodeCode:$code}) RETURN id(n) AS id",parameters).single().get("id").asLong());
            assertEquals(2,tx.run("MATCH (n:Entity {nodeCode:$code})-[r]->(m:Entity {name:$code}) RETURN count(r) AS total",parameters).single().get("total").asInt());
            sync.bindProjection(tx,code,name);
            tx.rollback();
        }
    }
}
