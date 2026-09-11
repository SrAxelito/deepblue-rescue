DeepBlue Rescue

Es la capa de persistencia de un sistema para centros que rescatan animales marinos. Cuando encuentran un animal herido, se registra el caso de rescate, el animal, su expediente médico, y los especialistas le hacen tratamientos hasta que se recupera. El proyecto solo cubre la parte de base de datos (entidades, repositories y migraciones), no tiene API ni frontend.

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

# Instrucciones para ejecutar

Necesitas tener Docker Desktop abierto antes de compilar, porque los tests lo necesitan para levantar PostgreSQL.

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

Docker Desktop tiene que estar corriendo, porque los tests usan Testcontainers para levantar un PostgreSQL real.

```bash
mvn test
```

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

En vez de usar una base de datos falsa en memoria (como H2), los tests levantan un contenedor real de PostgreSQL usando Docker, automáticamente, cada vez que se corren. Esto se hace con las anotaciones `@Testcontainers`, `@Container` y `@ServiceConnection` en la clase de test. Así uno se asegura de que las constraints (UNIQUE, FOREIGN KEY, CHECK) se están probando contra una base de datos real y no contra una simulación que a veces no se comporta igual.

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
- `ExpertiseRepository.findByNameIgnoreCase(String name)`
- `TreatmentRepository.findByAnimalIdOrderByPerformedAtAsc(Long animalId)`

# Listado de consultas JPQL implementadas

- `SpecialistRepository.findActiveByExpertise(String expertiseName)` - especialistas activos con determinada expertise
- `TreatmentRepository.findBetweenDates(LocalDateTime start, LocalDateTime end)` - tratamientos entre dos fechas
- `TreatmentRepository.findByCenterCode(String centerCode)` - tratamientos de animales de un centro (navega Treatment → Animal → RescueCase → RescueCenter)
- `TreatmentRepository.findBySpecialistExpertise(String expertiseName)` - tratamientos hechos por especialistas con determinada expertise
- `AnimalRepository.findInRehabilitationTreatedBySpecialistWithExpertise(RescueStatus status, String expertiseName)` - animales en cierto estado, tratados por especialista con cierta expertise
