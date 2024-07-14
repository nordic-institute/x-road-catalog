FROM rockylinux:9
MAINTAINER "Nordic Institute for Interoperability Solutions (NIIS)" <x-road@niis.org>

RUN dnf -y install sudo git rpm-build systemd-rpm-macros systemd java-21-openjdk-headless \
    && dnf clean all \
    && sed -i 's/requiretty/!requiretty/' /etc/sudoers

ARG CATALOG_PROFILE
ENV CATALOG_PROFILE=${CATALOG_PROFILE}
USER root
RUN git config --global --add safe.directory /workspace
CMD /workspace/xroad-catalog-lister/build_rpm.sh -p $CATALOG_PROFILE
