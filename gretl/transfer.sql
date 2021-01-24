SELECT 
	kote,
	ST_SubDivide(geometrie) AS geometrie,
	jahr
FROM 
	agi_hoehenkurven_2014.hoehenkurven_hoehenkurve
;