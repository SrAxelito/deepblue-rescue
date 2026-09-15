package com.deepblue.rescue.service;

import com.deepblue.rescue.domain.Animal;
import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.dto.response.AnimalResponse;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.mapper.AnimalMapper;
import com.deepblue.rescue.repository.AnimalRepository;
import com.deepblue.rescue.service.impl.AnimalServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnimalServiceImplTest {

    @Mock
    private AnimalRepository repository;

    @Mock
    private AnimalMapper mapper;

    @InjectMocks
    private AnimalServiceImpl service;

    private Animal buildAnimal(RescueStatus status) {
        RescueCase rescueCase = mock(RescueCase.class);
        when(rescueCase.getStatus()).thenReturn(status);

        Animal animal = mock(Animal.class);
        when(animal.getRescueCase()).thenReturn(rescueCase);
        return animal;
    }

    @Test
    void shouldFindAnimalByCode() {
        Animal animal = buildAnimal(RescueStatus.IN_REHABILITATION);
        AnimalResponse response = mock(AnimalResponse.class);

        when(repository.findByAnimalCode("AN-001"))
                .thenReturn(Optional.of(animal));
        when(mapper.toResponse(animal)).thenReturn(response);

        AnimalResponse result = service.findByCode("AN-001");

        assertThat(result).isEqualTo(response);
    }

    @Test
    void shouldThrowWhenAnimalNotFound() {
        when(repository.findByAnimalCode("AN-999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCode("AN-999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldReturnAnimalsInRehabilitation() {
        Animal animal = buildAnimal(RescueStatus.IN_REHABILITATION);
        AnimalResponse response = mock(AnimalResponse.class);

        when(repository.findByRescueCaseStatus(RescueStatus.IN_REHABILITATION))
                .thenReturn(List.of(animal));
        when(mapper.toResponse(animal)).thenReturn(response);

        List<AnimalResponse> result = service.findAnimalsInRehabilitation();

        assertThat(result).containsExactly(response);
    }

    @Test
    void shouldReturnTrueWhenAnimalCanReceiveTreatment() {
        Animal animal = buildAnimal(RescueStatus.UNDER_EVALUATION);

        when(repository.findByAnimalCode("AN-001"))
                .thenReturn(Optional.of(animal));

        assertThat(service.canReceiveTreatment("AN-001")).isTrue();
    }

    @Test
    void shouldReturnFalseWhenAnimalCannotReceiveTreatment() {
        Animal animal = buildAnimal(RescueStatus.RELEASED);

        when(repository.findByAnimalCode("AN-001"))
                .thenReturn(Optional.of(animal));

        assertThat(service.canReceiveTreatment("AN-001")).isFalse();
    }
}