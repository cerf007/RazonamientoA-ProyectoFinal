import { useState, useEffect, useCallback, useRef } from 'react';

const BFA_TEST_LIMIT_MINUTES = 10;

export default function PortalExamen({ datosEvaluado, preguntasDelTest }) {
    // Inicializamos todas las preguntas del test como 'omitidas' por defecto
    const [respuestas, setRespuestas] = useState(() => {
        return preguntasDelTest.reduce((acc, preg) => {
            acc[preg.numero] = { opcionSeleccionada: '', esOmitida: true };
            return acc;
        }, {});
    });

    const [timeLeft, setTimeLeft] = useState(BFA_TEST_LIMIT_MINUTES * 60);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [testFinalizado, setTestFinalizado] = useState(false);

    // Guardamos las respuestas en un Ref para que el temporizador asíncrono
    // siempre acceda al valor en tiempo real sin provocar re-renders en cascada.
    const respuestasRef = useRef(respuestas);
    useEffect(() => {
        respuestasRef.current = respuestas;
    }, [respuestas]);

    // Enviar resultados al API REST de OpenXava
    const enviarResultados = useCallback(async (respuestasFinales) => {
        if (isSubmitting) return;
        setIsSubmitting(true);

        const payload = {
            codigoSesion: datosEvaluado.codigoSesion,
            evaluado: { ...datosEvaluado },
            horaInicio: datosEvaluado.horaInicio,
            horaFin: new Date().toISOString(),
            detalles: Object.keys(respuestasFinales).map((numPregunta) => ({
                numeroPregunta: parseInt(numPregunta),
                opcionSeleccionada: respuestasFinales[numPregunta].opcionSeleccionada,
                esOmitida: respuestasFinales[numPregunta].esOmitida,
            })),
        };

        console.log("Enviando el siguiente JSON al backend:", payload);

        try {
            const response = await fetch('/api/examen/guardar-respuestas', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });

            if (response.ok) {
                setTestFinalizado(true);
            } else {
                alert('Simulación: Payload generado. Cuando el API de Java esté listo, aquí persistirá en la BD.');
                setTestFinalizado(true);
            }
        } catch (error) {
            console.error('Error de red (Backend apagado):', error);
            alert('Se mostró el JSON en consola. (El backend de OpenXava aún no está escuchando).');
            setTestFinalizado(true);
        } finally {
            setIsSubmitting(false);
        }
    }, [datosEvaluado, isSubmitting]);

    // ⏳ Temporizador optimizado para cumplir estrictamente con las reglas de ESLint
    useEffect(() => {
        const timer = setInterval(() => {
            setTimeLeft((prev) => {
                if (prev <= 1) {
                    clearInterval(timer);
                    // 🚀 Usamos setTimeout con 0ms para romper la ejecución síncrona
                    // sacando el envío del ciclo de renderizado inmediato (adiós error de la línea 63)
                    setTimeout(() => {
                        enviarResultados(respuestasRef.current);
                    }, 0);
                    return 0;
                }
                return prev - 1;
            });
        }, 1000);

        return () => clearInterval(timer);
    }, [enviarResultados]);

    const handleOptionChange = (numeroPregunta, opcion) => {
        setRespuestas((prev) => ({
            ...prev,
            [numeroPregunta]: { opcionSeleccionada: opcion, esOmitida: opcion === '' },
        }));
    };

    const formatTime = (seconds) => {
        const mins = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
    };

    if (testFinalizado) {
        return (
            <div style={{ padding: '40px', textAlign: 'center', fontFamily: 'sans-serif' }}>
                <h2>🎉 Prueba Finalizada con Éxito</h2>
                <p>Las respuestas (incluyendo las omitidas) fueron procesadas automáticamente por el sistema.</p>
            </div>
        );
    }

    return (
        <div style={{ padding: '20px', fontFamily: 'sans-serif', maxWidth: '800px', margin: '0 auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '2px solid #ccc', paddingBottom: '10px' }}>
                <h2>🧠 BFA: Razonamiento Forma A</h2>
                <div style={{ padding: '10px', background: timeLeft < 60 ? '#ffdddd' : '#eeddff', borderRadius: '5px', fontWeight: 'bold', color: timeLeft < 60 ? 'red' : 'black' }}>
                    ⏳ Tiempo restante: {formatTime(timeLeft)}
                </div>
            </div>

            <div style={{ margin: '20px 0' }}>
                {preguntasDelTest.map((preg) => (
                    <div key={preg.numero} style={{ background: '#f9f9f9', padding: '15px', marginBottom: '15px', borderRadius: '8px', border: '1px solid #eee' }}>
                        <p><strong>Pregunta {preg.numero}:</strong> {preg.serieIncompleta}</p>
                        <div style={{ display: 'flex', gap: '20px', marginTop: '10px' }}>
                            {['A', 'B', 'C', 'D'].map((opc) => (
                                <label key={opc} style={{ cursor: 'pointer' }}>
                                    <input
                                        type="radio"
                                        name={`preg-${preg.numero}`}
                                        value={opc}
                                        checked={respuestas[preg.numero]?.opcionSeleccionada === opc}
                                        onChange={() => handleOptionChange(preg.numero, opc)}
                                        style={{ marginRight: '5px' }}
                                    />
                                    {opc}) {preg[`opcion${opc}`]}
                                </label>
                            ))}
                        </div>
                    </div>
                ))}
            </div>

            <button
                onClick={() => enviarResultados(respuestas)}
                disabled={isSubmitting}
                style={{ padding: '12px 24px', background: '#0066cc', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '16px' }}
            >
                {isSubmitting ? 'Enviando...' : 'Finalizar y Entregar Test'}
            </button>
        </div>
    );
}