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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

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

    @Test
    void shouldFindRescueCasesByStatus() {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean Center", "Santa Marta");

        RescueCase case1 = new RescueCase("RES-001", LocalDate.of(2026, 8, 1), "Bahía Concha", RescueStatus.IN_REHABILITATION);
        RescueCase case2 = new RescueCase("RES-002", LocalDate.of(2026, 8, 2), "Playa Blanca", RescueStatus.READY_FOR_RELEASE);
        RescueCase case3 = new RescueCase("RES-003", LocalDate.of(2026, 8, 3), "Taganga", RescueStatus.IN_REHABILITATION);

        center.addCase(case1);
        center.addCase(case2);
        center.addCase(case3);

        rescueCenterRepository.save(center);
        rescueCaseRepository.saveAll(List.of(case1, case2, case3));
        rescueCaseRepository.flush();

        List<RescueCase> result = rescueCaseRepository.findByStatusOrderByRescueDateAsc(RescueStatus.IN_REHABILITATION);

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldFindAnimalsByCenterCodeOnly() {
        RescueCenter centerCar = new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta");
        RescueCenter centerPac = new RescueCenter("DB-PAC", "DeepBlue Pacific", "Buenaventura");

        RescueCase caseCar = new RescueCase("RES-CAR-1", LocalDate.of(2026, 8, 1), "Bahía Concha", RescueStatus.ADMITTED);
        RescueCase casePac = new RescueCase("RES-PAC-1", LocalDate.of(2026, 8, 1), "Isla Gorgona", RescueStatus.ADMITTED);

        centerCar.addCase(caseCar);
        centerPac.addCase(casePac);

        Animal animalCar = new Animal("AN-CAR-1", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE);
        Animal animalPac = new Animal("AN-PAC-1", "Olive Ridley Turtle", "Lepidochelys olivacea", AnimalSex.MALE);

        caseCar.assignAnimal(animalCar);
        casePac.assignAnimal(animalPac);

        rescueCenterRepository.saveAll(List.of(centerCar, centerPac));
        rescueCaseRepository.saveAll(List.of(caseCar, casePac));
        animalRepository.saveAll(List.of(animalCar, animalPac));
        animalRepository.flush();

        List<Animal> result = animalRepository.findByRescueCaseRescueCenterCode("DB-CAR");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAnimalCode()).isEqualTo("AN-CAR-1");
    }

    @Test
    void shouldFindActiveSpecialistsByExpertise() {
        Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
        Expertise rehabilitation = expertiseRepository.findByNameIgnoreCase("Rehabilitation").orElseThrow();
        Expertise marineMammals = expertiseRepository.findByNameIgnoreCase("Marine Mammals").orElseThrow();
        Expertise marineBirds = expertiseRepository.findByNameIgnoreCase("Marine Birds").orElseThrow();

        Specialist elena = new Specialist("SPEC-001", "Elena", "Vargas", "elena@deepblue.org", true);
        elena.addExpertise(trauma);
        elena.addExpertise(rehabilitation);

        Specialist mateo = new Specialist("SPEC-002", "Mateo", "Gomez", "mateo@deepblue.org", true);
        mateo.addExpertise(marineMammals);
        mateo.addExpertise(rehabilitation);

        Specialist sofia = new Specialist("SPEC-003", "Sofia", "Restrepo", "sofia@deepblue.org", true);
        sofia.addExpertise(marineBirds);
        sofia.addExpertise(trauma);

        specialistRepository.saveAll(List.of(elena, mateo, sofia));
        specialistRepository.flush();

        List<Specialist> result = specialistRepository.findActiveByExpertise("Trauma");

        assertThat(result).extracting(Specialist::getLastName)
                .containsExactlyInAnyOrder("Vargas", "Restrepo");
    }

    @Test
    void shouldPersistTreatmentsForAnimal() {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta");
        RescueCase rescueCase = new RescueCase("RES-2026-010", LocalDate.of(2026, 8, 10), "Bahía Concha", RescueStatus.IN_REHABILITATION);
        center.addCase(rescueCase);

        Animal animal = new Animal("AN-2026-010", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE);
        rescueCase.assignAnimal(animal);

        Specialist elena = new Specialist("SPEC-010", "Elena", "Vargas", "elena10@deepblue.org", true);
        Specialist mateo = new Specialist("SPEC-011", "Mateo", "Gomez", "mateo11@deepblue.org", true);

        rescueCenterRepository.save(center);
        rescueCaseRepository.save(rescueCase);
        animalRepository.save(animal);
        specialistRepository.saveAll(List.of(elena, mateo));

        Treatment t1 = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 10, 9, 0), TreatmentType.WOUND_CARE, "Wound cleaning");
        Treatment t2 = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 10, 11, 0), TreatmentType.HYDRATION, "Fluid therapy");
        Treatment t3 = new Treatment(animal, mateo, LocalDateTime.of(2026, 8, 10, 14, 0), TreatmentType.OBSERVATION, "General check");

        treatmentRepository.saveAll(List.of(t1, t2, t3));
        treatmentRepository.flush();

        assertThat(treatmentRepository.count()).isEqualTo(3);
    }

    @Test
    void shouldFindTreatmentsByAnimalOrderedChronologically() {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta");
        RescueCase rescueCase = new RescueCase("RES-2026-020", LocalDate.of(2026, 8, 12), "Bahía Concha", RescueStatus.IN_REHABILITATION);
        center.addCase(rescueCase);

        Animal animal = new Animal("AN-2026-020", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE);
        rescueCase.assignAnimal(animal);

        Specialist elena = new Specialist("SPEC-020", "Elena", "Vargas", "elena20@deepblue.org", true);

        rescueCenterRepository.save(center);
        rescueCaseRepository.save(rescueCase);
        animalRepository.save(animal);
        specialistRepository.save(elena);

        Treatment t1 = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 12, 9, 0), TreatmentType.WOUND_CARE, "Treatment 1");
        Treatment t2 = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 12, 11, 0), TreatmentType.HYDRATION, "Treatment 2");
        Treatment t3 = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 12, 14, 0), TreatmentType.OBSERVATION, "Treatment 3");

        treatmentRepository.saveAll(List.of(t3, t1, t2));
        treatmentRepository.flush();

        List<Treatment> result = treatmentRepository.findByAnimalIdOrderByPerformedAtAsc(animal.getId());

        assertThat(result).extracting(Treatment::getDescription)
                .containsExactly("Treatment 1", "Treatment 2", "Treatment 3");
    }

    @Test
    void shouldFindTreatmentsBetweenDates() {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta");
        RescueCase rescueCase = new RescueCase("RES-2026-030", LocalDate.of(2026, 8, 1), "Bahía Concha", RescueStatus.IN_REHABILITATION);
        center.addCase(rescueCase);

        Animal animal = new Animal("AN-2026-030", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE);
        rescueCase.assignAnimal(animal);

        Specialist elena = new Specialist("SPEC-030", "Elena", "Vargas", "elena30@deepblue.org", true);

        rescueCenterRepository.save(center);
        rescueCaseRepository.save(rescueCase);
        animalRepository.save(animal);
        specialistRepository.save(elena);

        Treatment t1 = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 1, 10, 0), TreatmentType.WOUND_CARE, "Early");
        Treatment t2 = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 10, 10, 0), TreatmentType.HYDRATION, "Middle");
        Treatment t3 = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 20, 10, 0), TreatmentType.OBSERVATION, "Late");

        treatmentRepository.saveAll(List.of(t1, t2, t3));
        treatmentRepository.flush();

        List<Treatment> result = treatmentRepository.findBetweenDates(
                LocalDateTime.of(2026, 8, 5, 0, 0),
                LocalDateTime.of(2026, 8, 15, 0, 0)
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDescription()).isEqualTo("Middle");
    }

    @Test
    void shouldViolateUniqueConstraintOnAnimalCode() {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta");
        rescueCenterRepository.save(center);

        RescueCase case1 = new RescueCase("RES-100-1", LocalDate.of(2026, 8, 1), "Bahía Concha", RescueStatus.ADMITTED);
        center.addCase(case1);
        Animal animal1 = new Animal("AN-100", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE);
        case1.assignAnimal(animal1);

        rescueCaseRepository.save(case1);
        rescueCaseRepository.flush();

        RescueCase case2 = new RescueCase("RES-100-2", LocalDate.of(2026, 8, 2), "Playa Blanca", RescueStatus.ADMITTED);
        center.addCase(case2);
        Animal animal2 = new Animal("AN-100", "Loggerhead Turtle", "Caretta caretta", AnimalSex.MALE);
        case2.assignAnimal(animal2);

        assertThatThrownBy(() -> {
            rescueCaseRepository.save(case2);
            rescueCaseRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldPersistFullIntegratorScenario() {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta");

        RescueCase rescueCase = new RescueCase("RES-2026-100", LocalDate.of(2026, 8, 18),
                "Bahía Concha", RescueStatus.IN_REHABILITATION);
        center.addCase(rescueCase);

        Animal animal = new Animal("AN-2026-100", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE);
        rescueCase.assignAnimal(animal);

        MedicalRecord record = new MedicalRecord(
                new BigDecimal("27.80"),
                "STABLE",
                "Injury caused by fishing net",
                "Possible plastic ingestion"
        );
        animal.assignMedicalRecord(record);

        Expertise marineReptiles = expertiseRepository.findByNameIgnoreCase("Marine Reptiles").orElseThrow();
        Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
        Expertise rehabilitation = expertiseRepository.findByNameIgnoreCase("Rehabilitation").orElseThrow();

        Specialist elena = new Specialist("SPEC-001", "Elena", "Vargas", "elena@deepblue.org", true);
        elena.addExpertise(marineReptiles);
        elena.addExpertise(trauma);
        elena.addExpertise(rehabilitation);

        rescueCenterRepository.save(center);
        rescueCaseRepository.save(rescueCase);
        animalRepository.save(animal);
        specialistRepository.save(elena);

        Treatment t1 = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 18, 10, 0),
                TreatmentType.WOUND_CARE, "Cleaning of left front flipper");
        Treatment t2 = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 18, 12, 0),
                TreatmentType.HYDRATION, "Subcutaneous fluid therapy");

        treatmentRepository.saveAll(List.of(t1, t2));
        treatmentRepository.flush();

        assertThat(rescueCaseRepository.findByCaseCode("RES-2026-100")).isPresent();
        assertThat(animalRepository.findByAnimalCode("AN-2026-100")).isPresent();
        assertThat(treatmentRepository.count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldResolveAllIntegratorScenarioQueries() {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta");
        RescueCase rescueCase = new RescueCase("RES-2026-100", LocalDate.of(2026, 8, 18),
                "Bahía Concha", RescueStatus.IN_REHABILITATION);
        center.addCase(rescueCase);

        Animal animal = new Animal("AN-2026-100", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE);
        rescueCase.assignAnimal(animal);

        Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
        Expertise rehabilitation = expertiseRepository.findByNameIgnoreCase("Rehabilitation").orElseThrow();

        Specialist elena = new Specialist("SPEC-001", "Elena", "Vargas", "elena@deepblue.org", true);
        elena.addExpertise(trauma);
        elena.addExpertise(rehabilitation);

        rescueCenterRepository.save(center);
        rescueCaseRepository.save(rescueCase);
        animalRepository.save(animal);
        specialistRepository.save(elena);

        Treatment t1 = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 18, 10, 0),
                TreatmentType.WOUND_CARE, "Cleaning of left front flipper");
        Treatment t2 = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 18, 12, 0),
                TreatmentType.HYDRATION, "Subcutaneous fluid therapy");
        treatmentRepository.saveAll(List.of(t1, t2));
        treatmentRepository.flush();

        assertThat(rescueCaseRepository.findByCaseCode("RES-2026-100")).isPresent();

        assertThat(rescueCaseRepository.findByStatusOrderByRescueDateAsc(RescueStatus.IN_REHABILITATION))
                .isNotEmpty();

        assertThat(animalRepository.findByRescueCaseRescueCenterCode("DB-CAR")).hasSize(1);

        assertThat(animalRepository.findByCommonNameContainingIgnoreCase("turtle")).isNotEmpty();

        assertThat(specialistRepository.findActiveByExpertise("Trauma")).isNotEmpty();

        Animal found = animalRepository.findByAnimalCode("AN-2026-100").orElseThrow();
        assertThat(treatmentRepository.findByAnimalIdOrderByPerformedAtAsc(found.getId())).hasSize(2);

        assertThat(treatmentRepository.findBySpecialistExpertise("Rehabilitation")).isNotEmpty();

        assertThat(treatmentRepository.findBetweenDates(
                LocalDateTime.of(2026, 8, 17, 0, 0),
                LocalDateTime.of(2026, 8, 19, 0, 0)
        )).hasSize(2);
    }

    @Test
    void shouldFindAnimalsInRehabilitationTreatedBySpecialistWithTraumaExpertise() {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta");
        RescueCase rescueCase = new RescueCase("RES-2026-200", LocalDate.of(2026, 8, 20),
                "Bahía Concha", RescueStatus.IN_REHABILITATION);
        center.addCase(rescueCase);

        Animal animal = new Animal("AN-2026-200", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE);
        rescueCase.assignAnimal(animal);

        Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
        Specialist elena = new Specialist("SPEC-200", "Elena", "Vargas", "elena200@deepblue.org", true);
        elena.addExpertise(trauma);

        rescueCenterRepository.save(center);
        rescueCaseRepository.save(rescueCase);
        animalRepository.save(animal);
        specialistRepository.save(elena);

        Treatment treatment = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 20, 9, 0),
                TreatmentType.WOUND_CARE, "Trauma treatment");
        treatmentRepository.save(treatment);
        treatmentRepository.flush();

        List<Animal> result = animalRepository.findInRehabilitationTreatedBySpecialistWithExpertise(
                RescueStatus.IN_REHABILITATION, "Trauma");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAnimalCode()).isEqualTo("AN-2026-200");
    }

}