package com.yupi.springbootinit.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yupi.springbootinit.config.LearningTransactionConfig;
import com.yupi.springbootinit.learning.*;
import org.neo4j.driver.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.*;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.Level;
import org.slf4j.LoggerFactory;

/** Explicit manual tool, never discovered as a test or started by the application.
 * Uses the existing transactional services described in docs 09/11/12, without an HTTP login bypass.
 */
public class LocalDraftImport {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.INDENT_OUTPUT);
    // Explicit registration below processes @Bean methods; omit @Configuration so the
    // normal application's test component scan cannot activate this local database setup.
    @EnableTransactionManagement
    @Import({LearningTransactionConfig.class,CourseRepository.class,CourseContentService.class,
            CourseGraphSyncService.class,NodeIdentityService.class,TeachingAssessmentService.class,
            AssessmentAssetService.class,ExperimentMaintenanceService.class})
    static class Config {
        @Bean DataSource dataSource() {
            DriverManagerDataSource ds=new DriverManagerDataSource();ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            ds.setUrl("jdbc:mysql://127.0.0.1:3306/oslab_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
            ds.setUsername(requiredEnv("OSLAB_IMPORT_DB_USER"));ds.setPassword("true".equals(System.getenv("OSLAB_IMPORT_DB_PASSWORD_EMPTY"))?"":requiredEnv("OSLAB_IMPORT_DB_PASSWORD"));return ds;
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds){return new JdbcTemplate(ds);}
        @Bean(destroyMethod="close") Driver driver(){return GraphDatabase.driver("bolt://127.0.0.1:7687",AuthTokens.none());}
    }
    static String requiredEnv(String key){String v=System.getenv(key);if(v==null)throw new IllegalArgumentException("Set "+key+" explicitly");return v;}
    static void write(Path path,Object value)throws Exception{JSON.writeValue(path.toFile(),value);}
    static String hash(byte[] bytes)throws Exception{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format("%02x",b&255));return out.toString();}
    static void archive(Path root,Path destination)throws Exception {
        Files.createDirectories(destination);List<Map<String,Object>> manifest=new ArrayList<>();
        try(Stream<Path> walk=Files.walk(root)) {for(Path file:walk.sorted().collect(Collectors.toList())){
            if(Files.isSymbolicLink(file))throw new IllegalArgumentException("Source symlink forbidden: "+file);
            if(!Files.isRegularFile(file))continue;
            Path relative=root.relativize(file),copy=destination.resolve(relative);Files.createDirectories(copy.getParent());
            byte[] bytes=Files.readAllBytes(file);Files.copy(file,copy,StandardCopyOption.COPY_ATTRIBUTES);
            if(!hash(bytes).equals(hash(Files.readAllBytes(copy))))throw new IllegalStateException("Archive mismatch");
            Map<String,Object> item=new LinkedHashMap<>();item.put("path",relative.toString().replace('\\','/'));item.put("bytes",bytes.length);item.put("sha256",hash(bytes));manifest.add(item);
        }}write(destination.resolve("archive-manifest.json"),manifest);
    }
    static ObjectNode request(Path root,String filename)throws Exception {
        ObjectNode request=JSON.createObjectNode();request.set("package",JSON.readTree(Files.readAllBytes(root.resolve(filename))));
        ArrayNode assets=request.putArray("assets"),files=request.putArray("files");
        for(String folder:Arrays.asList("assets","experiment-files")) {
            Path base=root.resolve(folder);if(!Files.isDirectory(base))continue;
            try(Stream<Path> walk=Files.walk(base)){for(Path file:walk.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                ObjectNode item=("assets".equals(folder)?assets:files).addObject();item.put("path",root.relativize(file).toString().replace('\\','/'));
                item.put("contentBase64",Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
                if("assets".equals(folder)){String mime=Files.probeContentType(file);item.put("contentType",mime==null?"image/png":mime);}
            }}
        }return request;
    }
    static Map<String,Object> state(JdbcTemplate jdbc)throws Exception {
        Map<String,Object> out=new LinkedHashMap<>();
        for(String table:Arrays.asList("knowledge_node_registry","knowledge_node_relation","learning_content_chunk","course_content_revision","course_asset","learning_question","learning_question_revision","question","experiment_revision","experiment_node","learning_unit","course_resource","learning_event","learning_attempt","learning_mastery","question_submit","course_import_run"))
            out.put(table,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class));
        for(String table:Arrays.asList("question","experiment_node","learning_event","learning_attempt","learning_mastery","question_submit")) {
            List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM "+table+" ORDER BY id");out.put(table+"Sha256",hash(JSON.writeValueAsBytes(rows)));
        }
        out.put("chunksByStatus",jdbc.queryForList("SELECT reviewStatus,COUNT(*) n FROM learning_content_chunk GROUP BY reviewStatus"));
        out.put("questionRevisionsByStatus",jdbc.queryForList("SELECT reviewStatus,COUNT(*) n FROM learning_question_revision GROUP BY reviewStatus"));
        out.put("experimentRevisionsByStatus",jdbc.queryForList("SELECT reviewStatus,COUNT(*) n FROM experiment_revision GROUP BY reviewStatus"));
        out.put("publishedUnits",jdbc.queryForObject("SELECT COUNT(*) FROM learning_unit WHERE publishedJson IS NOT NULL",Long.class));return out;
    }
    public static void main(String[] args)throws Exception {
        ((LoggerContext)LoggerFactory.getILoggerFactory()).getLogger("ROOT").setLevel(Level.WARN);
        if(args.length!=6||!("--preview-local".equals(args[0])||"--commit-local-drafts".equals(args[0])))
            throw new IllegalArgumentException("--preview-local|--commit-local-drafts adminId NEW_OUTPUT_DIR KNOWLEDGE_DIR QUESTIONS_DIR EXPERIMENT_DIR");
        boolean commit="--commit-local-drafts".equals(args[0]);long user=Long.parseLong(args[1]);Path output=Paths.get(args[2]).toAbsolutePath();
        if(Files.exists(output))throw new IllegalArgumentException("Output directory must be new");Files.createDirectory(output);
        String[] names={"knowledge","questions","experiments"},filenames={"knowledge.json","questions.json","experiment-maintenance.json"};ObjectNode[] requests=new ObjectNode[3];
        for(int i=0;i<3;i++){Path root=Paths.get(args[i+3]).toRealPath();Path copy=output.resolve(names[i]+"-original");archive(root,copy);requests[i]=request(copy,filenames[i]);}
        try(AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext(Config.class)){
            JdbcTemplate jdbc=context.getBean(JdbcTemplate.class);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND userRole='admin' AND isDelete=0",Long.class,user)!=1L)throw new IllegalArgumentException("Existing local admin required");
            Map<String,Object> operation=new LinkedHashMap<>();operation.put("mode",args[0]);operation.put("adminId",String.valueOf(user));operation.put("executionContext","L local offline service import; existing admin is the recorded actor, without login or account changes");operation.put("database",jdbc.queryForObject("SELECT DATABASE()",String.class));operation.put("started",java.time.Instant.now().toString());write(output.resolve("operation.json"),operation);
            Map<String,Object> before=state(jdbc);write(output.resolve("database-before.json"),before);
            Map<String,String> oldQuestions=new LinkedHashMap<>();
            for(Map<String,Object> row:jdbc.queryForList("SELECT * FROM learning_question ORDER BY id"))oldQuestions.put(row.get("id").toString(),hash(JSON.writeValueAsBytes(row)));
            write(output.resolve("existing-question-hashes.json"),oldQuestions);
            Driver driver=context.getBean(Driver.class);
            try(Session session=driver.session()){write(output.resolve("graph-before.json"),session.run("MATCH (n) OPTIONAL MATCH (n)-[r]->() RETURN count(DISTINCT n) AS nodes,count(r) AS relationships").single().asMap());}
            CourseContentService knowledge=context.getBean(CourseContentService.class);TeachingAssessmentService questions=context.getBean(TeachingAssessmentService.class);ExperimentMaintenanceService experiments=context.getBean(ExperimentMaintenanceService.class);
            for(int i=0;i<3;i++) {
                Map<String,Object> preview=i==0?knowledge.preview(requests[i]):i==1?questions.preview(requests[i]):experiments.preview(requests[i]);write(output.resolve(names[i]+"-preview.json"),preview);
                System.out.println(names[i]+" valid="+preview.get("valid")+" created="+preview.get("created")+" updated="+preview.get("updated")+" errors="+preview.get("errors"));
                if(commit&&Boolean.TRUE.equals(preview.get("valid"))) {
                    Map<String,Object> result=i==0?knowledge.commit(requests[i],user):i==1?questions.commit(requests[i],user):experiments.commit(requests[i],user);write(output.resolve(names[i]+"-commit.json"),result);
                    write(output.resolve(names[i]+"-repreview.json"),i==0?knowledge.preview(requests[i]):i==1?questions.preview(requests[i]):experiments.preview(requests[i]));
                }else if(commit&&i==2&&!((List<?>)preview.get("items")).isEmpty()) {
                    // Preserve the complete original preview; stage only exactly valid original entries.
                    Set<String> validCodes=new HashSet<>();for(Object entry:(List<?>)preview.get("items"))validCodes.add(((Map<?,?>)entry).get("code").toString());
                    ObjectNode subset=requests[i].deepCopy();ArrayNode selected=((ObjectNode)subset.path("package")).putArray("experiments");
                    for(JsonNode experiment:requests[i].path("package").path("experiments"))if(validCodes.contains(experiment.path("resourceCode").asText()))selected.add(experiment);
                    write(output.resolve("experiments-valid-subset-request.json"),subset);write(output.resolve("experiments-valid-subset-preview.json"),experiments.preview(subset));
                    write(output.resolve("experiments-valid-subset-commit.json"),experiments.commit(subset,user));write(output.resolve("experiments-valid-subset-repreview.json"),experiments.preview(subset));
                }
            }
            write(output.resolve("graph-sync.json"),context.getBean(CourseGraphSyncService.class).status());Map<String,Object> after=state(jdbc);write(output.resolve("database-after.json"),after);
            for(String table:Arrays.asList("question","experiment_node","learning_event","learning_attempt","learning_mastery","question_submit"))
                if(!before.get(table+"Sha256").equals(after.get(table+"Sha256")))throw new IllegalStateException("Protected data changed: "+table);
            for(Map.Entry<String,String> old:oldQuestions.entrySet())if(!old.getValue().equals(hash(JSON.writeValueAsBytes(jdbc.queryForMap("SELECT * FROM learning_question WHERE id=?",old.getKey())))))throw new IllegalStateException("Existing assessment changed: "+old.getKey());
            try(Session session=driver.session()){write(output.resolve("graph-after.json"),session.run("MATCH (n) OPTIONAL MATCH (n)-[r]->() RETURN count(DISTINCT n) AS nodes,count(r) AS relationships").single().asMap());}
            operation.put("finished",java.time.Instant.now().toString());operation.put("protectedDataUnchanged",true);write(output.resolve("operation.json"),operation);
        }
    }
}
