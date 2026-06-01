create table FILE_PATHS
(
    SET_ID     uuid not null,
    PATH       varchar_casesensitive(2048) not null,
    LOWER_PATH varchar2(2048) not null
);

alter table FILE_PATHS
    add constraint FILE_PATH_PK
        primary key (SET_ID, PATH);

create index IDX_FILE_PATHS_BY_LOWER_PATH
    on FILE_PATHS (SET_ID, LOWER_PATH);

create table FILE_METADATA
(
    SET_ID                          uuid   not null,
    ID                              uuid   not null,
    FILE_SYSTEM_KEY                 varchar2(1024),
    ABSOLUTE_PATH                   varchar_casesensitive(2048) not null,
    ORIGINAL_HASH                   varchar2(128),
    ORIGINAL_SIZE_BYTES             bigint not null,
    ORIGINAL_HASH_AND_SIZE          varchar2(2048) not null,
    PATH_AND_SIZE_AND_LAST_MODIFIED varchar2(2148) not null,
    LAST_MODIFIED                   bigint not null,
    LAST_ACCESSED                   bigint not null,
    CREATED                         bigint not null,
    POSIX_PERMISSIONS               varchar2(9) not null,
    OWNER_NAME                      varchar2(128) not null,
    GROUP_NAME                      varchar2(128) not null,
    FILE_TYPE                       ENUM('DIRECTORY', 'REGULAR_FILE', 'SYMBOLIC_LINK', 'OTHER', 'MISSING')    not null,
    HIDDEN                          bit    not null,
    STATUS                          ENUM('NEW', 'NO_CHANGE', 'METADATA_CHANGED', 'CONTENT_CHANGED', 'ROLLED_BACK', 'DELETED')    not null,
    ARCHIVE_ID                      uuid,
    ERROR                           varchar2(2048)
);

alter table FILE_METADATA
    add constraint FILE_METADATA_PK
        primary key (SET_ID, ID);

create index IDX_FILE_METADATA_BY_TYPE
    on FILE_METADATA (SET_ID, FILE_TYPE);

create index IDX_FILE_METADATA_BY_ARCHIVE_ID
    on FILE_METADATA (SET_ID, ARCHIVE_ID);

create index IDX_FILE_METADATA_BY_STATUS
    on FILE_METADATA (SET_ID, STATUS);

create index IDX_FILE_METADATA_BY_HASH
    on FILE_METADATA (SET_ID, ORIGINAL_HASH_AND_SIZE);

create index IDX_FILE_METADATA_BY_TIME
    on FILE_METADATA (SET_ID, PATH_AND_SIZE_AND_LAST_MODIFIED);

create table ARCHIVED_FILE_METADATA
(
    SET_ID           uuid not null,
    ID               uuid not null,
    BACKUP_INCREMENT int  not null,
    ARCHIVE          varchar2(128) not null,
    ORIGINAL_HASH    varchar2(128),
    ARCHIVED_HASH    varchar2(128)
);

alter table ARCHIVED_FILE_METADATA
    add constraint ARCHIVED_FILE_METADATA_PK
        primary key (SET_ID, ID);

create index IDX_ARCHIVED_FILE_METADATA_BY_BACKUP_INCREMENT
    on ARCHIVED_FILE_METADATA (SET_ID, BACKUP_INCREMENT);

create index IDX_ARCHIVED_FILE_METADATA_BY_ARCHIVE
    on ARCHIVED_FILE_METADATA (SET_ID, ARCHIVE);

create table ARCHIVE_FILE_METADATA_FILES
(
    SET_ID  uuid not null,
    ID      uuid not null,
    VERSION int  not null,
    FILE    uuid not null
);

alter table ARCHIVE_FILE_METADATA_FILES
    add constraint ARCHIVE_FILE_METADATA_FILES_PK
        primary key (SET_ID, ID, VERSION, FILE);

create index IDX_ARCHIVE_FILE_METADATA_FILES_BY_FILE
    on ARCHIVE_FILE_METADATA_FILES (SET_ID, FILE);

create table CHANGE_STATUS
(
    SET_ID        uuid not null,
    ABSOLUTE_PATH varchar_casesensitive(2048) not null,
    STATUS        ENUM('NEW', 'NO_CHANGE', 'METADATA_CHANGED', 'CONTENT_CHANGED', 'ROLLED_BACK', 'DELETED')    not null
);

alter table CHANGE_STATUS
    add constraint CHANGE_STATUS_PK
        primary key (SET_ID, ABSOLUTE_PATH);

create index IDX_CHANGE_STATUS_BY_CHANGE
    on CHANGE_STATUS (SET_ID, STATUS);
