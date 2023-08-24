START LONG TRANSACTION WRITE PRESERVE demo;

insert into demo(pk,col2,col3,col4,col5,cal6,col7) values (201,1,1.1,1.1,'1','1111', NULL);
insert into demo(pk,col2,col3,col4,col5,cal6,col7) values (202,2,2.2,2.2,'2','2222', NULL);
insert into demo(pk,col2,col3,col4,col5,cal6,col7) values (203,3,3.3,3.3,'3','3333', NULL);

COMMIT;

