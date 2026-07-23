# to connect with the server

docker run --detach --name mariadb -p 3330:3306 --env MARIADB_ROOT_PASSWORD=root mariadb:11.8

# remove all the running images

docker system prune -a -f

# run the mariadb

docker exec -it mariadb mariadb -u root -p

password is root