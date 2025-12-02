INSERT INTO tasks (name, description, created_by, assign_to, status, create_date)
SELECT seed.name, seed.description, seed.created_by, seed.assign_to, seed.status, seed.create_date
FROM (
   SELECT
      'Set up project' AS name,
      'Create Spring Boot + Thymeleaf task app' AS description,
      'Or' AS created_by,
      'Or' AS assign_to,
      'IN_PROGRESS' AS status,
      NOW() AS create_date
   UNION ALL
   SELECT
      'Write documentation',
      'Add README with setup instructions',
      'Or',
      'Dev1',
      'TODO',
      NOW()
   UNION ALL
   SELECT
      'Design database schema',
      'Create initial ERD and normalize tables for tasks module',
      'Dev1',
      'Dev2',
      'TODO',
      NOW()
   UNION ALL
   SELECT
      'Implement Task controller',
      'Add CRUD endpoints and connect to service/repository layer',
      'Dev2',
      'Dev2',
      'IN_PROGRESS',
      NOW()
   UNION ALL
   SELECT
      'UI layout for task list',
      'Style Thymeleaf templates for list and form views',
      'Dev3',
      'Dev3',
      'TODO',
      NOW()
   UNION ALL
   SELECT
      'Add validation and error messages',
      'Use Bean Validation annotations and show errors in the form',
      'Or',
      'Dev1',
      'TODO',
      NOW()
   UNION ALL
   SELECT
      'Smoke test in staging',
      'Run basic flows: create, edit, delete, list tasks',
      'QA1',
      'QA1',
      'TODO',
      NOW()
   UNION ALL
   SELECT
      'Prepare deployment checklist',
      'Define steps for config, DB migrations, and rollback plan',
      'PM1',
      'PM1',
      'TODO',
      NOW()
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM tasks);
