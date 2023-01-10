drop table fba_load;
drop table score;
drop table events;

create table fba_load(
 id           serial primary key, 
 ins_datetime TIMESTAMP default timeofday()::TIMESTAMP
);

create table events(
	 id           serial primary key, 
	 fba_load_id  integer not null constraint fk_event_fba_load references fba_load(id) on delete cascade,
	 event_id     integer,
	 event_number integer,
	 competitionName text,
	 skid         integer,
	 skname       text,
	 timerSeconds integer,
	 team1Id      integer,
	 team1        text, 
	 team2Id      integer,
	 team2        text,
	 startTimeTimestamp integer,
	 eventName    text,
     ins_datetime TIMESTAMP default timeofday()::TIMESTAMP
);

create table score(
 events_id    integer not null constraint fk_score_events references events(id) on delete cascade,
 team1        text,
 team1Coeff   numeric,
 team1score   text,
 draw         text,
 draw_coeff   numeric,
 team2Coeff   numeric,
 team2        text,
 team2score   text
);

drop table tgroup;

-- таблица с ид пользователей, создавших чат с ботом.
-- один чат, один пользователь.
create table tgroup(
 groupId       integer primary key, 
 ins_datetime  TIMESTAMP default timeofday()::TIMESTAMP,
 firstname     text,
 lastname      text,
 username      text,
 lang          text, 
 loc_latitude  numeric,
 loc_longitude numeric,
 last_cmd_start_dt TIMESTAMP
);

/*
drop table chat_status;

create table chat_status(
 id_chat                integer not null constraint fk_chat_satus_chat references chat(id) on delete cascade,
 ins_datetime           TIMESTAMP default timeofday()::TIMESTAMP,
 is_blocked_by_user     integer not null,
 is_blocked_by_admin    integer not null,
 id_blocked_by_admin_dt TIMESTAMP default timeofday()::TIMESTAMP,
 constraint ch_blocked check((is_blocked_by_user=0 and is_blocked_by_admin=0) or (is_blocked_by_admin=1))
);
*/
delete from tgroup;
select * from tgroup;

INSERT INTO tgroup(groupid,firstname,lastname,username,lang,loc_latitude,loc_longitude) 
VALUES(322134338,'Aleksey','Yakushev','AlexGruPerm','ru',12.0,34.0) 
ON CONFLICT (groupid) 
do update set last_cmd_start_dt = timeofday()::TIMESTAMP;


/*
16:30:29.194 [default-akka.actor.default-dispatcher-8] INFO fb.telegBotWH -  LASTNAME  = Yakushev
16:30:29.195 [default-akka.actor.default-dispatcher-8] INFO fb.telegBotWH -  USERNAME  = AlexGruPerm
16:30:29.197 [default-akka.actor.default-dispatcher-8] INFO fb.telegBotWH -   chat(id) = 322134338

16:30:42.666 [default-akka.actor.default-dispatcher-8] INFO fb.telegBotWH -  FIRSTNAME = Светлана
16:30:42.666 [default-akka.actor.default-dispatcher-8] INFO fb.telegBotWH -  LASTNAME  = Якушева
16:30:42.666 [default-akka.actor.default-dispatcher-8] INFO fb.telegBotWH -  USERNAME  = YakushevaSveta
16:30:42.666 [default-akka.actor.default-dispatcher-8] INFO fb.telegBotWH -   chat(id) = 533534191
*/

select * from chat_status;

delete from fba_load;
-- delete from events;
-- delete from score;

select * from fba_load;


select * 
from events e 
where e.skid=1 
order by team1,e.fba_load_id desc;

select s.*
from score s;

select 
       e.event_id, 
       e.skname,
       e.competitionname,
       e.timerseconds,
       e.eventname,
       null as "coefficient:",
       s.team1coeff,
       s.draw_coeff,
       s.team2coeff,
       null as "scores",
       s.team1score,
       s.team2score,
       e.timerseconds/60 as DurrationSeconds
 --e.*,s.* 
from events e 
left join score s on e.id = s.events_id
where e.skid = 1 
     -- событие существует в последней загрузке 
  and exists(
             select 1 
             from   events ei  
             where  ei.event_id  = e.event_id 
           )
    -- хоть по одной команде не 0
    and (s.team1score != '0' or s.team2score != '0')       
order by e.competitionname,e.eventname, e.ins_datetime desc;





