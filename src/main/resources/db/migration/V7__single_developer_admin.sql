update app_user
set email = 'admin@admin.dev',
    name = 'Desenvolvedor',
    password_hash = '$2b$10$wFiaXjzHqfDCohEVEoXCde7YAkYPDkTFr2mfw5YZUxb81DScP0GvK',
    updated_at = now(),
    changed_at = now()
where id = '20000000-0000-0000-0000-000000000001';
