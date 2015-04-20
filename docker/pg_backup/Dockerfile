FROM debian:jessie
MAINTAINER Fede "gnu.fede@gmail.com"
EXPOSE 8000

RUN apt-get update -y && \
    apt-get install -y postgresql-client rsyslog cron && \
    apt-get clean -y

# Add crontab file in the cron directory
ADD crontab /etc/cron.d/postgres-cron

ADD pg_backup.sh /usr/bin/pg_backup.sh
RUN chmod 0744 /usr/bin/pg_backup.sh

ADD var_change.py /usr/bin/var_change.py
RUN chmod 0744 /usr/bin/var_change.py

# Give execution rights on the cron job
RUN chmod 0644 /etc/cron.d/postgres-cron

# Create the log file to be able to run tail
RUN touch /var/log/cron.log


# Run the command on container startup
CMD /usr/bin/var_change.py && cron && cd /shared && python -m SimpleHTTPServer 8000
#&& tail -f /var/log/cron.log

#CMD /usr/bin/pg_backup.sh
#ENTRYPOINT ["/usr/bin/pg_backup.sh"]
