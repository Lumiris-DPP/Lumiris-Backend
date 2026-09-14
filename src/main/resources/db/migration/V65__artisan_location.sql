ALTER TABLE artisan_profiles ADD COLUMN location GEOMETRY(Point, 4326);
CREATE INDEX idx_artisan_profiles_location ON artisan_profiles USING GIST (location);
