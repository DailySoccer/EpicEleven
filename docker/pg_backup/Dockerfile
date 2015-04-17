FROM debian:jessie
MAINTAINER Fede "gnu.fede@gmail.com"

RUN apt-get update -y && \
    apt-get install -y postgresql-client rsyslog cron && \
    apt-get clean -y

# Add crontab file in the cron directory
ADD crontab /etc/cron.d/postgres-cron

ADD pg_backup.sh /usr/bin/pg_backup.sh
RUN chmod 0744 /usr/bin/pg_backup.sh

# Give execution rights on the cron job
RUN chmod 0644 /etc/cron.d/postgres-cron

# Create the log file to be able to run tail
RUN touch /var/log/cron.log

# Run the command on container startup
CMD cron && tail -f /var/log/cron.log

#CMD /usr/bin/pg_backup.sh
#ENTRYPOINT ["/usr/bin/pg_backup.sh"]
