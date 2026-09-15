# DeepBlue Rescue

Sistema para centros que rescatan y rehabilitan animales marinos. Cuando encuentran un animal herido, se registra el caso de rescate, el animal, su expediente médico, y los especialistas le hacen tratamientos hasta que se recupera.

El proyecto ahora cubre dos capas:

- **Persistencia**: entidades, repositories y migraciones (laboratorio 1).
- **Servicio**: reglas de negocio, DTOs, mappers y excepciones (laboratorio 2).

Todavía no tiene Controllers, API REST, Spring Security ni frontend — eso queda para laboratorios posteriores.

# Modelo de datos

Las tablas/entidades son:

- `RescueCenter` - los centros de rescate
- `RescueCase` - los casos de rescate
- `Animal` - el animal rescatado
- `MedicalRecord` - el expediente médico del animal
- `Specialist` - los especialistas
- `Expertise` - las áreas de experiencia (Trauma, Rehabilitation, etc.)
- `Treatment` - los tratamientos hechos al animal
- `specialist_expertise` - tabla intermedia (no es una entidad de negocio, solo conecta Specialist con Expertise)

# Relaciones

- `RescueCenter` → `RescueCase`: 1:N (un centro tiene muchos casos)
- `RescueCase` → `Animal`: 1:1 (un caso tiene un solo animal)
- `Animal` → `MedicalRecord`: 1:1 (un animal tiene un solo expediente)
- `Specialist` ↔ `Expertise`: N:M (un especialista puede tener varias expertises, y viceversa)
- `Animal` → `Treatment`: 1:N (un animal puede recibir muchos tratamientos)
- `Specialist` → `Treatment`: 1:N (un especialista puede hacer muchos tratamientos)

# Arquitectura

```
Controller (futuro)
      ↓
   Service
      ↓
 Repository
      ↓
  Hibernate
      ↓
 PostgreSQL
```

La capa Service es responsable de:

- validar reglas de negocio antes de guardar (especialista activo, caso no liberado, fecha válida, transición de estado permitida);
- transformar Entity ↔ DTO mediante MapStruct;
- controlar transacciones (`@Transactional`);
- lanzar excepciones específicas (`ResourceNotFoundException`, `BusinessRuleException`) en vez de dejar que el Controller o el Repository asuman esa responsabilidad.

# Instrucciones para ejecutar

```bash
mvn clean install
```

Si quieres correr la aplicación contra una base de datos propia, configura las variables de entorno (o usa las que ya vienen por defecto en `application.yml`):

```bash
DB_URL=jdbc:postgresql://localhost:5432/deepblue
DB_USER=postgres
DB_PASSWORD=postgres
```

# Instrucciones para ejecutar tests

Hay dos tipos de tests en el proyecto, y solo uno de ellos necesita Docker:

**Unit tests de la capa Service** (Mockito, sin base de datos real):

```bash
mvn test -Dtest=RescueCaseServiceImplTest,TreatmentServiceImplTest,AnimalServiceImplTest
```

Estos NO necesitan Docker. Los repositories están mockeados, así que no se levanta ningún PostgreSQL real.

**Tests de integración de la capa de persistencia** (Testcontainers, con PostgreSQL real):

```bash
mvn test
```

Estos SÍ necesitan Docker Desktop abierto, porque `PersistenceIntegrationTest` levanta un contenedor real de PostgreSQL.

# Explicación de Flyway

Las tablas las crea Flyway, no Hibernate. En el `application.yml` está configurado:

```yaml
ddl-auto: validate
```

en vez de `update`. Esto significa que Hibernate no crea ni modifica ninguna tabla, solo revisa que las entidades Java coincidan con lo que ya existe en la base (creado por Flyway). Si no coinciden, la aplicación no arranca.

Las migraciones están en `src/main/resources/db/migration`:

- `V1__create_schema.sql` - crea las 8 tablas con sus constraints (PK, FK, UNIQUE, CHECK)
- `V2__insert_expertise_catalog.sql` - inserta el catálogo inicial de expertise
- `V3__add_tracking_device_to_animal.sql` - agrega la columna del código del dispositivo GPS a `animals`

# Explicación de Testcontainers

En vez de usar una base de datos falsa en memoria (como H2), los tests de integración levantan un contenedor real de PostgreSQL usando Docker, automáticamente, cada vez que se corren. Esto se hace con las anotaciones `@Testcontainers`, `@Container` y `@ServiceConnection` en la clase de test. Así uno se asegura de que las constraints (UNIQUE, FOREIGN KEY, CHECK) se están probando contra una base de datos real y no contra una simulación que a veces no se comporta igual.

Los unit tests de la capa Service, en cambio, deliberadamente **no** usan Testcontainers ni `@SpringBootTest` — el objetivo es probar la lógica de negocio aislada, reemplazando los repositories con mocks de Mockito.

# DTOs

Se usan `record` de Java para evitar exponer las entidades directamente hacia otras capas:

**Response:**
- `RescueCaseResponse`
- `TreatmentResponse`
- `AnimalResponse`

**Request:**
- `ChangeRescueStatusRequest`
- `CreateTreatmentRequest`

# Mappers (MapStruct)

- `RescueCaseMapper` - `RescueCase` → `RescueCaseResponse`
- `TreatmentMapper` - `Treatment` → `TreatmentResponse`
- `AnimalMapper` - `Animal` → `AnimalResponse`

El proyecto combina **Lombok** y **MapStruct** en el mismo `pom.xml`. Para que MapStruct pueda ver los getters generados por Lombok, el `maven-compiler-plugin` declara los `annotationProcessorPaths` en un orden específico: `lombok` → `lombok-mapstruct-binding` → `mapstruct-processor`.

# Excepciones personalizadas

- `ResourceNotFoundException` - el recurso solicitado no existe (animal, caso o especialista no encontrado).
- `BusinessRuleException` - el recurso existe, pero la operación no está permitida (especialista inactivo, caso ya liberado/cerrado, transición de estado inválida, fecha de tratamiento anterior a la fecha de rescate).

# Servicios y reglas de negocio

## RescueCaseService

- `findByCode(caseCode)` - búsqueda por código, lanza `ResourceNotFoundException` si no existe.
- `findByStatus(status)` - lista casos según estado, ordenados por fecha de rescate.
- `changeStatus(caseCode, request)` - cambia el estado del caso validando la transición permitida:

```
ADMITTED → UNDER_EVALUATION → IN_REHABILITATION → READY_FOR_RELEASE → RELEASED
```

Cualquier transición fuera de esa secuencia lanza `BusinessRuleException` y nunca llega a guardar.

## TreatmentService

- `findByAnimalCode(animalCode)` - tratamientos de un animal ordenados cronológicamente.
- `register(request)` - registra un tratamiento validando, en orden:
    1. el animal existe (`ResourceNotFoundException`);
    2. el especialista existe (`ResourceNotFoundException`);
    3. el especialista está activo (`BusinessRuleException`);
    4. el caso del animal no está `RELEASED` ni `CLOSED` (`BusinessRuleException`);
    5. la fecha del tratamiento no es anterior a la fecha de rescate (`BusinessRuleException`).

## AnimalService (reto integrador)

- `findByCode(animalCode)` - búsqueda por código.
- `findAnimalsInRehabilitation()` - animales cuyo caso está `IN_REHABILITATION`.
- `canReceiveTreatment(animalCode)` - retorna `true` solo si el caso del animal está `UNDER_EVALUATION` o `IN_REHABILITATION`.

# Unit tests de la capa Service

Se prueban con JUnit + Mockito + AssertJ, sin Spring context y sin Docker:

- `RescueCaseServiceImplTest` - caso encontrado, caso no encontrado, transición válida (`save()` se ejecuta), transición inválida (`save()` nunca se ejecuta).
- `TreatmentServiceImplTest` - registro válido, especialista inactivo, caso liberado — en los dos últimos se verifica con `verify(treatmentRepository, never()).save(any())` que la violación de una regla de negocio nunca llega a persistirse.
- `AnimalServiceImplTest` - búsqueda por código, animal no encontrado, animales en rehabilitación, `canReceiveTreatment` en `true` y en `false`.

Las entidades (`RescueCase`, `Animal`, `Specialist`) tienen constructores `protected` por diseño de JPA, así que en los tests se usan mocks (`mock(RescueCase.class)`, etc.) en vez de instanciarlas directamente.

# Listado de Query Methods implementados

- `RescueCenterRepository.findByCode(String code)`
- `RescueCaseRepository.findByCaseCode(String caseCode)`
- `RescueCaseRepository.findByStatusOrderByRescueDateAsc(RescueStatus status)`
- `RescueCaseRepository.findByRescueCenterCode(String code)`
- `RescueCaseRepository.findByRescueDateAfterOrderByRescueDateDesc(LocalDate date)`
- `AnimalRepository.findByAnimalCode(String animalCode)`
- `AnimalRepository.findByCommonNameContainingIgnoreCase(String text)`
- `AnimalRepository.findByRescueCaseStatus(RescueStatus status)`
- `AnimalRepository.findByRescueCaseRescueCenterCode(String centerCode)`
- `SpecialistRepository.findByProfessionalCode(String professionalCode)`
- `ExpertiseRepository.findByNameIgnoreCase(String name)`
- `TreatmentRepository.findByAnimalIdOrderByPerformedAtAsc(Long animalId)`

# Listado de consultas JPQL implementadas

- `SpecialistRepository.findActiveByExpertise(String expertiseName)` - especialistas activos con determinada expertise
- `TreatmentRepository.findBetweenDates(LocalDateTime start, LocalDateTime end)` - tratamientos entre dos fechas
- `TreatmentRepository.findByCenterCode(String centerCode)` - tratamientos de animales de un centro (navega Treatment → Animal → RescueCase → RescueCenter)
- `TreatmentRepository.findBySpecialistExpertise(String expertiseName)` - tratamientos hechos por especialistas con determinada expertise
- `AnimalRepository.findInRehabilitationTreatedBySpecialistWithExpertise(RescueStatus status, String expertiseName)` - animales en cierto estado, tratados por especialista con cierta expertise