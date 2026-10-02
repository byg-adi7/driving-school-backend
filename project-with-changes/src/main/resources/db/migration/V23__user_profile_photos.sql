-- ============================================================
-- users: a profile photo for every account (students, instructors, admins),
-- uploaded to Cloudinary. profile_image_public_id is Cloudinary's id, kept
-- so a replaced or removed photo can be deleted there too.
-- ============================================================
ALTER TABLE users ADD COLUMN profile_image_url VARCHAR(500);
ALTER TABLE users ADD COLUMN profile_image_public_id VARCHAR(255);

-- Students had a free-text image URL on their profile; carry any values over
-- to the account, then drop it - the photo now only comes from an upload.
UPDATE users u SET profile_image_url = sp.profile_image_url
FROM student_profiles sp
WHERE sp.user_id = u.id AND sp.profile_image_url IS NOT NULL AND sp.profile_image_url <> '';

ALTER TABLE student_profiles DROP COLUMN profile_image_url;
