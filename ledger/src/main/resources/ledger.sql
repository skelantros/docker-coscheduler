create table ledger(
	id               serial,
	session_id       varchar(32),
	task_title       varchar(256),
	node_id          varchar(256),
	event            varchar(16),
	instant_time     bigint,
	time_after_start bigint
)