-- Use an isolated test database. Assign @benchmark_seller to an existing account ID first.
INSERT INTO products(seller_id,title,description,category,price,stock,status)
WITH RECURSIVE digits(n) AS (SELECT 0 UNION ALL SELECT n+1 FROM digits WHERE n<9)
SELECT @benchmark_seller,
  CONCAT('BENCH catalogue ',10000*a.n+1000*b.n+100*c.n+10*d.n+e.n),
  'Catalog performance fixture',
  ELT(1+MOD(e.n,5),'BOOKS','ELECTRONICS','DAILY','SPORTS','OTHER'),
  29.90,3,'ACTIVE'
FROM digits a CROSS JOIN digits b CROSS JOIN digits c CROSS JOIN digits d CROSS JOIN digits e
WHERE a.n < 3;
