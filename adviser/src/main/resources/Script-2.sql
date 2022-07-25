
select 
       e.event_id, 
       e.skname,
       e.competitionname,
       --e.timerseconds,
       e.eventname,
       null as "coefficient:",
       s.team1coeff, 
       s.draw_coeff,
       s.team2coeff,
       null as "scores",
       s.team1score, 
       s.team2score,
       e.timerseconds/60 as DurrationMinutes
 --e.*,s.* 
from events e 
left join score s on e.id = s.events_id
where --e.skid = 1 
     -- событие существует в последней загрузке 
  --and 
   (s.team1coeff is not null or s.team2coeff is not null) 
  and exists(
             select 1 
             from   events ei  
             where  ei.event_id  = e.event_id 
           )
    -- хоть по одной команде не 0
   -- and (s.team1score != '0' or s.team2score != '0')   
   -- and e.event_id = 35206665
order by e.competitionname,e.eventname, e.ins_datetime desc;