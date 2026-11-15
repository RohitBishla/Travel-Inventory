-- The app creates and migrates travel_shard_0 and travel_shard_1 itself; it only needs a user allowed to.
CREATE USER IF NOT EXISTS 'travel'@'%' IDENTIFIED BY 'travel';
CREATE USER IF NOT EXISTS 'travel'@'localhost' IDENTIFIED BY 'travel';
GRANT ALL PRIVILEGES ON `travel\_%`.* TO 'travel'@'%';
GRANT ALL PRIVILEGES ON `travel\_%`.* TO 'travel'@'localhost';
FLUSH PRIVILEGES;
