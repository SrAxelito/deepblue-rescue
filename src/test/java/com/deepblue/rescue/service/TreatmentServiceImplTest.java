package com.deepblue.rescue.service;

import com.deepblue.rescue.domain.*;
import com.deepblue.rescue.dto.request.CreateTreatmentRequest;
import com.deepblue.rescue.exception.BusinessRuleException;
import com.deepblue.rescue.mapper.TreatmentMapper;
import com.deepblue.rescue.repository.AnimalRepository;
import com.deepblue.rescue.repository.SpecialistRepository;
import com.deepblue.rescue.repository.TreatmentRepository;
import com.deepblue.rescue.service.impl.TreatmentServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TreatmentServiceImplTest {

    @Mock private AnimalRepository animalRepository;
    @Mock private SpecialistRepository specialistRepository;
    @Mock private TreatmentRepository treatmentRepository;
    @Mock private TreatmentMapper mapper;

    @InjectMocks
    private TreatmentServiceImpl service;

    private Animal buildAnimal(RescueStatus status, LocalDate rescueDate) {
        RescueCase rescueCase = mock(RescueCase.class);
        when(rescueCase.getStatus()).thenReturn(status);
        when(rescueCase.getRescueDate()).thenReturn(rescueDate);

        Animal animal = mock(Animal.class);
        when(animal.getRescueCase()).thenReturn(rescueCase);
        return animal;
    }

    private Specialist buildSpecialist(boolean active) {
        Specialist specialist = mock(Specialist.class);
        when(specialist.isActive()).thenReturn(active);
        return specialist;
    }

    @Test
    void shouldRegisterTreatmentWhenValid() {
        Animal animal = buildAnimal(RescueStatus.IN_REHABILITATION, LocalDate.of(2026, 8, 1));
        Specialist specialist = buildSpecialist(true);

        when(animalRepository.findByAnimalCode("AN-001"))
                .thenReturn(Optional.of(animal));
        when(specialistRepository.findByProfessionalCode("SPEC-001"))
                .thenReturn(Optional.of(specialist));
        when(treatmentRepository.save(any(Treatment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateTreatmentRequest request = new CreateTreatmentRequest(
                "AN-001", "SPEC-001",
                LocalDateTime.of(2026, 8, 10, 9, 0),
                TreatmentType.WOUND_CARE, "Cleaning"
        );

        service.register(request);

        verify(treatmentRepository).save(any(Treatment.class));
    }

    @Test
    void shouldThrowWhenSpecialistInactive() {
        Animal animal = buildAnimal(RescueStatus.IN_REHABILITATION, LocalDate.of(2026, 8, 1));
        Specialist specialist = buildSpecialist(false);

        when(animalRepository.findByAnimalCode("AN-001"))
                .thenReturn(Optional.of(animal));
        when(specialistRepository.findByProfessionalCode("SPEC-001"))
                .thenReturn(Optional.of(specialist));

        CreateTreatmentRequest request = new CreateTreatmentRequest(
                "AN-001", "SPEC-001",
                LocalDateTime.of(2026, 8, 10, 9, 0),
                TreatmentType.OBSERVATION, "Check"
        );

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessRuleException.class);

        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenCaseReleased() {
        Animal animal = buildAnimal(RescueStatus.RELEASED, LocalDate.of(2026, 8, 1));
        Specialist specialist = buildSpecialist(true);

        when(animalRepository.findByAnimalCode("AN-001"))
                .thenReturn(Optional.of(animal));
        when(specialistRepository.findByProfessionalCode("SPEC-001"))
                .thenReturn(Optional.of(specialist));

        CreateTreatmentRequest request = new CreateTreatmentRequest(
                "AN-001", "SPEC-001",
                LocalDateTime.of(2026, 8, 10, 9, 0),
                TreatmentType.OBSERVATION, "Check"
        );

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessRuleException.class);

        verify(treatmentRepository, never()).save(any());
    }
}