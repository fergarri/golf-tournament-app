-- Calificación (Course Rating) y Slope Rating de Ida (hoyos 1-9) y Vuelta (hoyos 10-18)
-- por tee de salida. Necesarios para calcular el Course Handicap de 9 hoyos según la
-- Regla 6.1b de las Rules of Handicapping (USGA/R&A), ya que Slope/Rating de 9 hoyos
-- NO son necesariamente la mitad exacta de los valores de 18 hoyos.
ALTER TABLE course_tees
    ADD COLUMN course_rating_ida NUMERIC(4,1),
    ADD COLUMN course_rating_vuelta NUMERIC(4,1),
    ADD COLUMN slope_rating_ida INTEGER,
    ADD COLUMN slope_rating_vuelta INTEGER;
