// Also created by CourseGraphSyncService before the first import synchronization.
// Neo4j 4.4+; preserve historical nodes without nodeCode while enforcing stable identities.
CREATE CONSTRAINT course_entity_node_code IF NOT EXISTS FOR (n:Entity) REQUIRE n.nodeCode IS UNIQUE;
