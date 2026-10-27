-- Datos de prueba para el laboratorio de frontend

INSERT INTO rescue_centers (code, name, city) VALUES
    ('RC-SMR', 'Centro de Rescate Marino Santa Marta', 'Santa Marta'),
    ('RC-CTG', 'Centro de Rescate Marino Cartagena', 'Cartagena');

INSERT INTO rescue_cases (case_code, rescue_date, rescue_location, status, rescue_center_id) VALUES
    ('CASE-001', '2026-09-10', 'Playa El Rodadero',  'ADMITTED',          (SELECT id FROM rescue_centers WHERE code = 'RC-SMR')),
    ('CASE-002', '2026-09-15', 'Bahía de Taganga',   'ADMITTED',          (SELECT id FROM rescue_centers WHERE code = 'RC-SMR')),
    ('CASE-003', '2026-08-20', 'Parque Tayrona',     'UNDER_EVALUATION',  (SELECT id FROM rescue_centers WHERE code = 'RC-SMR')),
    ('CASE-004', '2026-08-01', 'Islas del Rosario',  'IN_REHABILITATION', (SELECT id FROM rescue_centers WHERE code = 'RC-CTG')),
    ('CASE-005', '2026-07-05', 'Bocagrande',         'READY_FOR_RELEASE', (SELECT id FROM rescue_centers WHERE code = 'RC-CTG')),
    ('CASE-006', '2026-06-12', 'Playa Blanca',       'RELEASED',          (SELECT id FROM rescue_centers WHERE code = 'RC-CTG'));

INSERT INTO animals (animal_code, common_name, scientific_name, sex, rescue_case_id) VALUES
    ('AN-001', 'Tortuga carey',       'Eretmochelys imbricata', 'FEMALE',  (SELECT id FROM rescue_cases WHERE case_code = 'CASE-001')),
    ('AN-002', 'Pelícano pardo',      'Pelecanus occidentalis', 'MALE',    (SELECT id FROM rescue_cases WHERE case_code = 'CASE-002')),
    ('AN-003', 'Tortuga verde',       'Chelonia mydas',         'UNKNOWN', (SELECT id FROM rescue_cases WHERE case_code = 'CASE-003')),
    ('AN-004', 'Manatí antillano',    'Trichechus manatus',     'FEMALE',  (SELECT id FROM rescue_cases WHERE case_code = 'CASE-004')),
    ('AN-005', 'Delfín nariz botella','Tursiops truncatus',     'MALE',    (SELECT id FROM rescue_cases WHERE case_code = 'CASE-005')),
    ('AN-006', 'Tortuga caguama',     'Caretta caretta',        'FEMALE',  (SELECT id FROM rescue_cases WHERE case_code = 'CASE-006'));

INSERT INTO medical_records (animal_id, initial_weight, initial_condition, injuries, observations) VALUES
    ((SELECT id FROM animals WHERE animal_code = 'AN-001'), 45.50, 'CRITICAL', 'Herida en aleta derecha', 'Enredada en red de pesca'),
    ((SELECT id FROM animals WHERE animal_code = 'AN-004'), 320.00, 'STABLE',  'Cortes por hélice',       'Responde bien a la alimentación');

INSERT INTO specialists (professional_code, first_name, last_name, email, active) VALUES
    ('SP-001', 'Laura',  'Gómez', 'laura.gomez@deepblue.org',  TRUE),
    ('SP-002', 'Andrés', 'Pérez', 'andres.perez@deepblue.org', TRUE),
    ('SP-003', 'Marta',  'Ruiz',  'marta.ruiz@deepblue.org',   FALSE);

INSERT INTO specialist_expertise (specialist_id, expertise_id) VALUES
    ((SELECT id FROM specialists WHERE professional_code = 'SP-001'), (SELECT id FROM expertise WHERE name = 'Marine Reptiles')),
    ((SELECT id FROM specialists WHERE professional_code = 'SP-001'), (SELECT id FROM expertise WHERE name = 'Trauma')),
    ((SELECT id FROM specialists WHERE professional_code = 'SP-002'), (SELECT id FROM expertise WHERE name = 'Marine Mammals')),
    ((SELECT id FROM specialists WHERE professional_code = 'SP-002'), (SELECT id FROM expertise WHERE name = 'Rehabilitation'));

INSERT INTO treatments (animal_id, specialist_id, performed_at, type, description) VALUES
    ((SELECT id FROM animals WHERE animal_code = 'AN-003'), (SELECT id FROM specialists WHERE professional_code = 'SP-001'),
    '2026-08-21 09:00', 'OBSERVATION', 'Evaluación inicial del caparazón'),
    ((SELECT id FROM animals WHERE animal_code = 'AN-004'), (SELECT id FROM specialists WHERE professional_code = 'SP-002'),
    '2026-08-02 10:30', 'WOUND_CARE', 'Limpieza y sutura de cortes por hélice'),
    ((SELECT id FROM animals WHERE animal_code = 'AN-004'), (SELECT id FROM specialists WHERE professional_code = 'SP-002'),
    '2026-08-10 15:00', 'NUTRITION', 'Inicio de dieta con lechuga y pasto marino');
