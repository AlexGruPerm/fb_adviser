

--delete from fba.events; 
-- 1. В первом запросе отбираем только нужные события по типу, фильтрам.
select e.event_id,e.competitionname ,e.team1 ,e.team2 , max(e.timerseconds)/60 as durr_mins
from events e 
left join score s on e.id = s.events_id
where 
    e.skid = 1 and -- только футбол
  (s.team1coeff is not null or s.team2coeff is not null) and  
   -- уже прошло 70 минут игры.
 --  e.timerseconds/60 > 70 and ----------- todo: он нужен
   -- игра ещё не закончилась
    not exists( 
    select 1
    from   events ei
    where  ei.event_id = e.event_id and 
           ei.timerseconds/60 >= 90    
   )
   -- есть события в близком интервале
   and 
   exists( 
		   select *
			from events ei
			where ei.event_id = e.event_id and 
			      ei.ins_datetime between timeofday()::TIMESTAMP + make_interval(mins => -3) and 
			                              timeofday()::TIMESTAMP
         )
   group by e.event_id,e.competitionname ,e.team1 ,e.team2
   order by durr_mins desc
 ;


   
-- вложенный цикл по отдельеным событиям
select 
       e.event_id,  
       e.skname,
       e.competitionname,
       --e.timerseconds,
       e.eventname,
       ----null as "coefficient:", 
       s.team1coeff, 
       -- round((1/s.team1coeff)*100,1) as team1prcnt, 
       s.draw_coeff,
       s.team2coeff,
       ----null as "scores",
       s.team1score, 
       s.team2score,
       e.timerseconds/60 as DurrMin,
       (case 
         when coalesce(s.team1coeff,0) between 1.25 and 1.35 OR
              coalesce(s.draw_coeff,0) between 1.25 and 1.35 or
              coalesce(s.team2coeff,0) between 1.25 and 1.35 
         then 1
         else null::integer
        end) as is_time
 --e.*,s.* 
from events e 
left join score s on e.id = s.events_id
where e.event_id = 36074233 and
     -- событие существует в последней загрузке 
  --and 
   (s.team1coeff is not null or s.team2coeff is not null) 
    -- хоть по одной команде не 0
   -- and (s.team1score != '0' or s.team2score != '0')   
order by e.competitionname,e.eventname, e.ins_datetime desc;


