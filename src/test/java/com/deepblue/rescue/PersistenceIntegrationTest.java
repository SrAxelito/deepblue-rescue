package com.deepblue.rescue;

import com.deepblue.rescue.domain.*;
import com.deepblue.rescue.repository.AnimalRepository;
import com.deepblue.rescue.repository.ExpertiseRepository;
import com.deepblue.rescue.repository.MedicalRecordRepository;
import com.deepblue.rescue.repository.RescueCaseRepository;
import com.deepblue.rescue.repository.RescueCenterRepository;
import com.deepblue.rescue.repository.SpecialistRepository;
import com.deepblue.rescue.repository.TreatmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@Transactional
class PersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18-alpine")
                    .withDatabaseName("deepblue_test")
                    .withUsername("deepblue")
                    .withPassword("deepblue");

    @Autowired
    private RescueCenterRepository rescueCenterRepository;

    @Autowired
    private RescueCaseRepository rescueCaseRepository;

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private SpecialistRepository specialistRepository;

    @Autowired
    private ExpertiseRepository expertiseRepository;

    @Autowired
    private TreatmentRepository treatmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayShouldHaveExecutedV1AndV2() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank",
                String.class
        );

        assertThat(versions).contains("1", "2");
    }

    @Test
    void shouldPersistAndRetrieveRescueCenterUsingInheritedMethods() {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean Center", "Santa Marta");

        RescueCenter saved = rescueCenterRepository.save(center);

        assertThat(saved.getId()).isNotNull();

        boolean exists = rescueCenterRepository.existsById(saved.getId());
        assertThat(exists).isTrue();

        var found = rescueCenterRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("DB-CAR");

        long total = rescueCenterRepository.count();
        assertThat(total).isEqualTo(1);
    }

    @Test
    void shouldPersistOneToManyRelationBetweenCenterAndCases() {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean Center", "Santa Marta");

        RescueCase case1 = new RescueCase("RES-001", LocalDate.of(2026, 8, 1),
                "Bahía Concha", RescueStatus.IN_REHABILITATION);
        RescueCase case2 = new RescueCase("RES-002", LocalDate.of(2026, 8, 5),
                "Playa Blanca", RescueStatus.ADMITTED);

        center.addCase(case1);
        center.addCase(case2);

        rescueCenterRepository.save(center);
        rescueCaseRepository.saveAll(List.of(case1, case2));
        rescueCaseRepository.flush();

        List<RescueCase> cases = rescueCaseRepository.findByRescueCenterCode("DB-CAR");

        assertThat(cases).hasSize(2);
        assertThat(cases)
                .extracting(RescueCase::getCaseCode)
                .containsExactlyInAnyOrder("RES-001", "RES-002");
    }
    @Test
    void shouldPersistOneToOneRelationBetweenCaseAndAnimal() {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean Center", "Santa Marta");

        RescueCase rescueCase = new RescueCase("RES-2026-001", LocalDate.of(2026, 8, 18),
                "Bahía Concha", RescueStatus.IN_REHABILITATION);

        center.addCase(rescueCase);

        Animal animal = new Animal("AN-2026-001", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE);

        rescueCase.assignAnimal(animal);

        rescueCenterRepository.save(center);
        rescueCaseRepository.save(rescueCase);
        animalRepository.save(animal);
        animalRepository.flush();

        RescueCase foundCase = rescueCaseRepository.findByCaseCode("RES-2026-001").orElseThrow();
        Animal foundAnimal = animalRepository.findByAnimalCode("AN-2026-001").orElseThrow();

        assertThat(foundCase.getAnimal().getAnimalCode()).isEqualTo("AN-2026-001");
        assertThat(foundAnimal.getRescueCase().getCaseCode()).isEqualTo("RES-2026-001");
    }

    @Test
    void shouldPersistOneToOneRelationBetweenAnimalAndMedicalRecord() {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean Center", "Santa Marta");
        RescueCase rescueCase = new RescueCase("RES-2026-002", LocalDate.of(2026, 8, 19),
                "Bahía Concha", RescueStatus.ADMITTED);
        center.addCase(rescueCase);

        Animal animal = new Animal("AN-2026-002", "Loggerhead Turtle", "Caretta caretta", AnimalSex.MALE);
        rescueCase.assignAnimal(animal);

        MedicalRecord record = new MedicalRecord(
                new BigDecimal("28.40"),
                "STABLE",
                "Left front flipper injury",
                null
        );
        animal.assignMedicalRecord(record);

        rescueCenterRepository.save(center);
        rescueCaseRepository.save(rescueCase);
        animalRepository.save(animal);
        animalRepository.flush();

        assertThat(animal.getId()).isNotNull();
        assertThat(record.getId()).isNotNull();
    }

    @Test
    void shouldPersistManyToManyRelationBetweenSpecialistAndExpertise() {
        Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
        Expertise rehabilitation = expertiseRepository.findByNameIgnoreCase("Rehabilitation").orElseThrow();

        Specialist elena = new Specialist("SPEC-001", "Elena", "Vargas", "elena@deepblue.org", true);
        elena.addExpertise(trauma);
        elena.addExpertise(rehabilitation);

        specialistRepository.save(elena);
        specialistRepository.flush();

        Specialist found = specialistRepository.findById(elena.getId()).orElseThrow();
        assertThat(found.getExpertiseAreas()).hasSize(2);
    }

}