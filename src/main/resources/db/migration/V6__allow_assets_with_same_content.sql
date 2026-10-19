-- Each upload or import is its own asset with its own name and source address; identical bytes are still stored once.
ALTER TABLE assets DROP CONSTRAINT uk_assets_content;
