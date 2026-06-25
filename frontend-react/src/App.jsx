import PortalExamen from './PortalExamen';

// Datos dummy de prueba del Evaluado
const mockupEvaluado = {
  codigoSesion: "BFA-2026-X9",
  edad: 19,
  numeroCedula: "001-020607-1005Q",
  sexo: "M",
  departamento: "Managua",
  institucion: "Privada",
  horaInicio: new Date().toISOString()
};

// Datos dummy de preguntas (Series incompletas clásicas de tests psicométricos)
const mockupPreguntas = [
  { numero: 1, serieIncompleta: "2, 4, 6, 8, ...", opcionA: "9", opcionB: "10", opcionC: "11", opcionD: "12" },
  { numero: 2, serieIncompleta: "9, 12, 15, 18, ...", opcionA: "20", opcionB: "21", opcionC: "22", opcionD: "23" },
  { numero: 3, serieIncompleta: "1, 4, 9, 16, ...", opcionA: "20", opcionB: "25", opcionC: "30", opcionD: "36" }
];

function App() {
  return (
      <PortalExamen
          datosEvaluado={mockupEvaluado}
          preguntasDelTest={mockupPreguntas}
      />
  );
}

export default App;
