# Custom Thread Pool.

## Анализ производительности:
corePoolSize=2, maximumPoolSize=4, keepAliveTime=5, queueSize=5.
1. Стандартный ThreadPool.
   <br/>
   ✅ Total Tasks: 10000
   <br/>
   ❌ Rejected Tasks: 1991
   <br/>
   ⚡ Throughput: 490,70 tasks/sec
   <br/>
   ⏳ Total time: 20379 ms

2. Кастомный ThreadPool.
      <br/>
      ✅ Total Tasks: 10000
      <br/>
      ❌ Rejected Tasks: 1979
      <br/>
      ⚡ Throughput: 490,17 tasks/sec
      <br/>
      ⏳ Total time: 20401 ms

На основе бенчмарков можно сделать вывод, что стандартный ThreadPool и кастомная имплементация практически идентичны в производительности.

## Зависимость производительности от параметров пула:
queueSize: увеличение queueSize позволяет уменьшить отклонения, но влечет за собой повышение задержки обработки т.к. задачи ждут в очереди, а не запускаются сразу.

corePoolSize: увеличение corePoolSize позволяет обработать больше задач параллельно, снижая отклонения, но слишком большое значение может привести к излишнему переключению контекста.

maxPoolSize: влияет на способность справляться с пиковыми нагрузками, но если слишком велик, возрастут накладные расходы на создание и поддержание потоков.

keepAliveTime: влияет на то, как быстро уменьшается количество потоков при снижении нагрузки — может быть полезно для экономии ресурсов, но мало влияет на throughput.

| Параметр   | Малое значение                                 | Среднее значение                             | Большое значение                    |
|------------|------------------------------------------------|---------------------------------------------|-------------------------------------|
| queueSize        | много отклонённых задач                                           | оптимальный баланс                                         | низкие отклонения, большая задержка |
| corePoolSize      | мало потоков — узкое горло                                          | достаточное число потоков                                        | слишком много — оверхед |
| maxPoolSize     | ограничивает пиковую нагрузку                                           | позволяет гибко масштабироваться                                        | много потоков — накладные расходы   |
| keepAliveTime     | быстро убирает простаивающие потоки                                            | сбалансирован                                          | долго держит потоки |


## Принцип действия механизма распределения задач между очередями и работы балансировки:
В этой реализации пула:
- Каждый рабочий поток (Worker) связан со своей собственной очередью задач.
- Когда поступает новая задача, балансировщик выбирает одну из этих очередей.
- Распределение происходит по алгоритму Round Robin — то есть задачи поочерёдно направляются в очереди разных воркеров, чтобы равномерно распределять нагрузку.

Каждый Worker:
- Постоянно обрабатывает задачи из своей очереди.
- Завершается, если простаивает дольше keepAliveTime и общее количество потоков превышает corePoolSize. 

Такой подход с индивидуальными очередями и простым Round Robin:
- Избегает блокировок между потоками, потому что каждый поток работает со своей очередью.
- Обеспечивает равномерную нагрузку без сложной синхронизации.
- Хорошо масштабируется и даёт стабильную производительность при разумных значениях параметров.
