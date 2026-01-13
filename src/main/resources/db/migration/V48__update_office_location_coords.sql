-- Update Schoolable HQ coordinates to the correct location
INSERT INTO office_locations (id, name, address, latitude, longitude, radius_meters, is_active)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Schoolable HQ',
    'Schoolable HQ',
    6.46875,
    3.54036,
    200,
    TRUE
)
ON CONFLICT (id) DO UPDATE
SET latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    address = EXCLUDED.address,
    is_active = TRUE;
