package com.sip.portal.demo;

import com.sip.portal.problem.*;
import com.sip.portal.user.*;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Fills an EMPTY database with fictional data. Off unless DEMO_DATA=true: its password is public. */
@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
class DemoDataLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private static final String PASSWORD = "Password123";

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final ProblemRepository problems;
    private final PasswordEncoder encoder;
    private final JsonMapper json;

    DemoDataLoader(UserRepository users, OrganizationRepository organizations, ProblemRepository problems,
            PasswordEncoder encoder, JsonMapper json) {
        this.users = users;
        this.organizations = organizations;
        this.problems = problems;
        this.encoder = encoder;
        this.json = json;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (users.count() > 0) return;
        String hash = encoder.encode(PASSWORD);
        Map<String, User> citizens = new HashMap<>();
        citizens.put("usr_cit_01", users.save(new User("Ramesh Kumar", "citizen.ramesh@gmail.com", hash,
                Role.CITIZEN, "9835123450", null)));
        users.save(new User("Admin Officer", "admin.dept@jharkhand.gov.in", hash, Role.ADMIN, null, null));
        users.save(new User("Faculty Lead", "faculty@bitmesra.ac.in", hash, Role.UNIVERSITY, null,
                organization("BIT Mesra Innovation Lab", Role.UNIVERSITY)));
        users.save(new User("CSR Manager", "csr@tatasteel.com", hash, Role.INDUSTRY, null,
                organization("Tata Steel CSR Foundation", Role.INDUSTRY)));

        // Other sample citizens cannot sign in: nobody knows their password
        String locked = encoder.encode(UUID.randomUUID().toString());
        try (InputStream in = new ClassPathResource("demo/sample-problems.json").getInputStream()) {
            for (JsonNode n : json.readTree(in)) {
                String key = n.at("/citizen/id").asString();
                User citizen = citizens.computeIfAbsent(key, k -> users.save(new User(
                        n.at("/citizen/name").asString(), k + "@demo.invalid", locked, Role.CITIZEN, null, null)));
                addProblem(n, citizen);
            }
        }
        log.warn("Demo data loaded with a publicly known password. Do not expose this server.");
    }

    private void addProblem(JsonNode n, User citizen) {
        Instant submitted = Instant.parse(n.get("submittedAt").asString());
        Problem p = problems.save(new Problem(citizen, n.get("title").asString(), n.get("description").asString(),
                Category.fromLabel(n.get("category").asString()).orElseThrow(), n.get("district").asString(),
                n.at("/location/address").asString(), n.at("/location/lat").asDouble(),
                n.at("/location/lng").asDouble(), n.get("photoUrl").asString(), submitted));
        p.assignCaseId(2026);
        for (JsonNode h : n.get("statusHistory")) {
            p.moveTo(ProblemStatus.valueOf(h.get("stage").asString()), h.get("changedBy").asString(),
                    h.get("remarks").asString(), Instant.parse(h.get("changedAt").asString()));
        }
        if (!n.get("rejectionReason").isNull()) p.setRejectionReason(n.get("rejectionReason").asString());
        JsonNode u = n.get("assignedUniversity");
        if (!u.isNull()) {
            p.setAssignment(new Assignment(p, organization(u.get("name").asString(), Role.UNIVERSITY),
                    u.get("department").asString(), u.get("facultyLead").asString(), u.get("studentsCount").asInt()));
        }
        JsonNode s = n.get("industrySupport");
        if (!s.isNull()) {
            p.setSupport(new IndustrySupport(p, organization(s.get("sponsor").asString(), Role.INDUSTRY),
                    s.get("type").asString(), Long.parseLong(s.get("amount").asString().replaceAll("\\D", ""))));
        }
        JsonNode f = n.get("feedback");
        if (!f.isNull()) {
            p.setFeedback(new Feedback(p, f.get("rating").asInt(), f.get("comment").asString(), null,
                    Instant.parse(f.get("submittedAt").asString())));
        }
    }

    private Organization organization(String name, Role type) {
        return organizations.findByTypeAndName(type, name)
                .orElseGet(() -> organizations.save(new Organization(name, type)));
    }
}
