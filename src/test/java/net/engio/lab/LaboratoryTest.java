package net.engio.lab;

import net.engio.pips.lab.Benchmark;
import net.engio.pips.lab.ExecutionContext;
import net.engio.pips.lab.LabException;
import net.engio.pips.lab.Laboratory;
import net.engio.pips.lab.workload.*;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author bennidi
 *         Date: 2/25/14
 */
public class LaboratoryTest extends UnitTest{

    public static ITaskFactory NoOperation = new ITaskFactory() {
        @Override
        public ITask create(ExecutionContext context) {
            return new ITask() {
                @Override
                public void run(ExecutionContext context) throws Exception {
                    // do nothing
                }
            };
        }
    };

    @Test
    public void testInvalidWorkloadSetups() throws Exception {
        Workload withoutFactory = new Workload("without factory")
                .starts().immediately()
                .duration().repetitions(1);

        Workload withoutStart = new Workload("without start")
                .setITaskFactory(NoOperation)
                .duration().repetitions(1);

        Workload withoutDuration = new Workload("without duration")
                .starts().immediately()
                .setITaskFactory(NoOperation);


        Workload cyclicStart1 = new Workload("without duration");
        Workload cyclicStart2 = new Workload("without duration");
        cyclicStart1
                .starts().after(cyclicStart2)
                .duration().repetitions(1)
                .setITaskFactory(NoOperation);
        cyclicStart2
                .starts().after(cyclicStart1)
                .duration().repetitions(1)
                .setITaskFactory(NoOperation);

        Workload cyclicDuration1 = new Workload("without duration");
        Workload cyclicDuration2 = new Workload("without duration");
        cyclicDuration1
                .starts().immediately()
                .duration().depends(cyclicDuration2)
                .setITaskFactory(NoOperation);
        cyclicDuration2
                .starts().immediately()
                .duration().depends(cyclicDuration1)
                .setITaskFactory(NoOperation);

        Laboratory lab = new Laboratory();

        try {
            lab.run(new Benchmark("Invalid").addWorkload(withoutFactory));
            fail();
        } catch (LabException e) {
            assertEquals(e.getCode(), LabException.ErrorCode.WLWithoutFactory);
        }

        try {
            lab.run(new Benchmark("Invalid").addWorkload(withoutStart));
            fail();
        } catch (LabException e) {
            assertEquals(e.getCode(), LabException.ErrorCode.WLWithoutStart);
        }

        try {
            lab.run(new Benchmark("Invalid").addWorkload(withoutDuration));
            fail();
        } catch (LabException e) {
            assertEquals(e.getCode(), LabException.ErrorCode.WLWithoutDuration);
        }

        try {
            lab.run(new Benchmark("Invalid").addWorkload(cyclicStart1, cyclicStart2));
            fail();
        } catch (LabException e) {
            assertEquals(e.getCode(), LabException.ErrorCode.WLWithCycleInStart);
        }

        try {
            lab.run(new Benchmark("Invalid").addWorkload(cyclicDuration1, cyclicDuration2));
            fail();
        } catch (LabException e) {
            assertEquals(e.getCode(), LabException.ErrorCode.WLWithCycleInDuration);
        }

    }

    @Test
    public void testWorkloadSchedulingStartingAfter() throws Exception {
        final AtomicInteger counter = new AtomicInteger(0);
        Workload first = new Workload("First workload")
                .setParallelTasks(15)
                .setITaskFactory(new ITaskFactory() {
                    @Override
                    public ITask create(ExecutionContext context) {
                        return new ITask() {
                            @Override
                            public void run(ExecutionContext context) throws Exception {
                                counter.incrementAndGet();
                            }
                        };
                    }
                })
                .duration().repetitions(1)
                .starts().immediately();

        Workload second = new Workload("Second Workload")
                .setParallelTasks(15)
                .setITaskFactory(new ITaskFactory() {
                    @Override
                    public ITask create(ExecutionContext context) {
                        return new ITask() {
                            @Override
                            public void run(ExecutionContext context) throws Exception {
                                counter.decrementAndGet();
                            }
                        };
                    }
                })
                .handle(ExecutionEvent.WorkloadInitialization, new ExecutionHandler() {
                    @Override
                    public void handle(ExecutionContext context) {
                        // the first workload
                        assertEquals(15, counter.get());
                    }
                })
                .duration().repetitions(1)
                .starts().after(first);

        Laboratory lab  = new Laboratory();
        lab.run(new Benchmark("test").addWorkload(first, second));

        Thread.sleep(100);
        // both have run in the end
        assertEquals(0, counter.get());
    }

    //@Test
    // TODO: this invalid start/end dependency tree must be fixed
    public void testWorkloadSchedulingStartingAfterDelayed() throws Exception {
        final AtomicInteger counter = new AtomicInteger(0);
        Workload first = new Workload("First workload")
                .setParallelTasks(15)
                .setITaskFactory(new ITaskFactory() {
                    @Override
                    public ITask create(ExecutionContext context) {
                        return new ITask() {
                            @Override
                            public void run(ExecutionContext context) throws Exception {
                                counter.incrementAndGet();
                            }
                        };
                    }
                })
                .duration().repetitions(1)
                .starts().after(2, TimeUnit.SECONDS);

        Workload second = new Workload("Second Workload")
                .setParallelTasks(15)
                .setITaskFactory(new ITaskFactory() {
                    @Override
                    public ITask create(ExecutionContext context) {
                        return new ITask() {
                            @Override
                            public void run(ExecutionContext context) throws Exception {
                                counter.decrementAndGet();
                            }
                        };
                    }
                })
                .handle(ExecutionEvent.WorkloadInitialization, new ExecutionHandler() {
                    @Override
                    public void handle(ExecutionContext context) {
                        // the first workload
                        assertEquals(15, counter.get());
                    }
                })
                .duration().depends(first)
                .starts().after(first);

        Laboratory lab  = new Laboratory();
        lab.run(new Benchmark("test").addWorkload(first, second));

        Thread.sleep(4000);
        // both have run in the end
        assertEquals(0, counter.get());
    }

    @Test
    public void testWorkloadShutdownCancelsTasks() throws Exception {
        final AtomicInteger first = new AtomicInteger(1);
        final AtomicInteger second = new AtomicInteger(1);

        Workload countUp = new Workload("First workload")
                .setParallelTasks(15)
                .setITaskFactory(new ITaskFactory() {
                    @Override
                    public ITask create(ExecutionContext context) {
                        return new ITask() {
                            @Override
                            public void run(ExecutionContext context) throws Exception {
                                first.incrementAndGet();
                            }
                        };
                    }
                })
                .duration().lasts(5, TimeUnit.SECONDS)
                .starts().immediately();

        Workload countDown = new Workload("Second Workload")
                .setParallelTasks(15)
                .setITaskFactory(new ITaskFactory() {
                    @Override
                    public ITask create(ExecutionContext context) {
                        return new ITask() {
                            @Override
                            public void run(ExecutionContext context) throws Exception {
                                second.incrementAndGet();
                            }
                        };
                    }
                })
                .duration().depends(countUp)
                .starts().immediately();

        Laboratory lab  = new Laboratory();
        lab.run(new Benchmark("test").addWorkload(countUp, countDown));

        int firstVal = first.get();
        int secondVal = second.get();
        // no threads that change the counter are running anymore
        Thread.sleep(1000);
        assertEquals(firstVal, first.get());
        assertEquals(secondVal, second.get());
        assertTrue(firstVal > 1);
        assertTrue(secondVal > 1);
    }

    @Test
    public void testWorkloadSchedulingDurationDepends() throws Exception {
        final AtomicLong start = new AtomicLong(0);
        final AtomicLong finish = new AtomicLong(0);
        final AtomicLong finishSecond = new AtomicLong(0);
        int duration = 3;
        Workload first = new Workload("First workload")
                .setParallelTasks(5)
                .duration().lasts(duration, TimeUnit.SECONDS)
                .starts().immediately()
                .setITaskFactory(NoOperation)
                .handle(ExecutionEvent.WorkloadInitialization, new ExecutionHandler() {
                    @Override
                    public void handle(ExecutionContext context) {
                        start.set(System.currentTimeMillis());
                    }
                })
                .handle(ExecutionEvent.WorkloadCompletion, new ExecutionHandler() {
                    @Override
                    public void handle(ExecutionContext context) {
                        finish.set(System.currentTimeMillis());
                    }
                });

        Workload second = new Workload("Second Workload")
                .setParallelTasks(5)
                .setITaskFactory(NoOperation)
                .handle(ExecutionEvent.WorkloadCompletion, new ExecutionHandler() {
                    @Override
                    public void handle(ExecutionContext context) {
                        // the first workload
                        finishSecond.set(System.currentTimeMillis());
                    }
                })
                .duration().depends(first)
                .starts().immediately();

        Laboratory lab  = new Laboratory();
        lab.run(new Benchmark("test").addWorkload(first, second));

        // first workload has run at least duration seconds
        assertTrue(finish.get() - start.get() >= duration * 1000);

        // second workload has not run significantly longer then first  (~1ms per thread)
        assertTrue(finishSecond.get() - finish.get() < 5);
    }

    @Test
    public void testMultipleDependencies() throws Exception {
        final AtomicBoolean finished = new AtomicBoolean(false);
        Workload first = new Workload("First workload")
                .setParallelTasks(5)
                .duration().lasts(10, TimeUnit.SECONDS)
                .starts().immediately()
                .setITaskFactory(NoOperation);

        Workload second = new Workload("Second Workload")
                .setParallelTasks(5)
                .setITaskFactory(NoOperation)
                .duration().depends(first)
                .starts().immediately();

        Workload third = new Workload("Third Workload")
                .setParallelTasks(5)
                .setITaskFactory(NoOperation)
                .duration().depends(first)
                .starts().immediately()
                .handle(ExecutionEvent.WorkloadCompletion, new ExecutionHandler() {
                    @Override
                    public void handle(ExecutionContext context) {
                        finished.set(true);
                    }
                });

        Laboratory lab  = new Laboratory();
        lab.run(new Benchmark("test").addWorkload(first, second, third));

        assertTrue(finished.get());
    }


}
