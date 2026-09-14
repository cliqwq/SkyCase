# Kills the test Scatha (should already be past pre-roll from scatha_kill.mcfunction) and gives a
# Fine Topaz Gemstone lookalike -- a real SCATHA pet/pet-data stack can't be faked from a datapack
# without the repo, so this only exercises the gemstone/fallback path of the result window.
kill @e[type=armor_stand,tag=skycase_scatha_test]
give @p minecraft:emerald[custom_name="Fine Topaz Gemstone",lore=["RARE"]]
