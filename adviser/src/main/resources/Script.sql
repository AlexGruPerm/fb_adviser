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





