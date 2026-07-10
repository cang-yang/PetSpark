-- Replace the original third-party placeholder with a repository-owned asset.
-- This migration is safe for both upgraded databases and fresh installations.

UPDATE operation_banner
SET image_url = '/banner-default.jpg',
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = '00000000-0000-0000-0000-000000000281'
  AND image_url = 'https://placehold.co/1200x360?text=PetSpark+Banner';
