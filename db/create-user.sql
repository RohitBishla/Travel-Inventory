-- The app creates its own shard databases; it just needs a user that may.
CREATE USER IF NOT EXISTS 'travel'@'%' IDENTIFIED BY 'travel';
CREATE USER IF NOT EXISTS 'travel'@'localhost' IDENTIFIED BY 'travel';
GRANT ALL PRIVILEGES ON `travel\_%`.* TO 'travel'@'%';
GRANT ALL PRIVILEGES ON `travel\_%`.* TO 'travel'@'localhost';
FLUSH PRIVILEGES;
