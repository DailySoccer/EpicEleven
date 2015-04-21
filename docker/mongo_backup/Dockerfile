FROM mongo:2.6
MAINTAINER Fede "gnu.fede@gmail.com"

RUN apt-get update -y && \
    apt-get install -y rsyslog cron python&& \
    apt-get clean -y

# Add crontab file in the cron directory
ADD crontab /etc/cron.d/mongo-cron

ADD mongo_backup.sh /usr/bin/mongo_backup.sh
RUN chmod 0744 /usr/bin/mongo_backup.sh

ADD var_change.py /usr/bin/var_change.py
RUN chmod 0744 /usr/bin/var_change.py

# Give execution rights on the cron job
RUN chmod 0644 /etc/cron.d/mongo-cron

# Create the log file to be able to run tail
RUN touch /var/log/cron.log


# Run the command on container startup
CMD /usr/bin/var_change.py && cron && cd /shared && python -m SimpleHTTPServer 8000
