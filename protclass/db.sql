create table panther_class ( pclass_id varchar(32) primary key, 
node_code varchar(64), 
node_level int(11), 
class_name varchar(1024), 
class_descr varchar(2048),
updated timestamp
) as select * from CSVREAD('/Users/guhar/panther_class.csv');

create table panther_uniprot_map (accession varchar(32), taxon_id int(11), pclass_id varchar(32), updated timestamp)
as select * from CSVREAD('/Users/guhar/panther_uniprot_map.csv');

create index idx_acc on panther_uniprot_map(accession);
create index idx_pclass_id_idx on panther_uniprot_map(pclass_id);

