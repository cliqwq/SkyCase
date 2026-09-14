# Scatha kill reveal test (task 15): spawns a Scatha name-tag armor stand already at/under the 15%
# pre-roll threshold (75/500 = 15%), tagged so the finish step can target it without relying on the
# name selector escaping the health readout correctly.
summon armor_stand ~ ~ ~2 {Invisible:1b,CustomNameVisible:1b,CustomName:'{"text":"[Lv50] Scatha 75/500❤"}',Tags:["skycase_scatha_test"]}
schedule function skycase:scatha_kill_finish 2s
