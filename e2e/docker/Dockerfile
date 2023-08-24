FROM ubuntu:20.04
ENV DEBCONF_NOWARNINGS=yes
RUN apt-get update && apt-get install -y curl
RUN apt-get remove cmdtest yarn
RUN curl -fsSL https://deb.nodesource.com/setup_16.x | bash -
RUN echo "deb https://dl.yarnpkg.com/debian/ stable main" | tee /etc/apt/sources.list.d/yarn.list
RUN curl https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add -
RUN apt-get update
RUN apt-get install -y nodejs yarn apt-utils curl
RUN npx playwright install
RUN npx playwright install-deps

CMD ["sh", "/app/run_test.sh"]