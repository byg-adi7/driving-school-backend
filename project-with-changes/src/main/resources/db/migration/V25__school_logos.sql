-- A school's logo, uploaded by its admin to Cloudinary. logo_public_id is
-- Cloudinary's id, kept so a replaced or removed logo can be deleted there too.
ALTER TABLE schools ADD COLUMN logo_url VARCHAR(500);
ALTER TABLE schools ADD COLUMN logo_public_id VARCHAR(255);
