package com.yammer.metrics.reporting;

import com.yammer.metrics.core.*;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A reporter which exposes application metric as JMX MBeans.
 */
public class JmxReporter extends AbstractReporter implements MetricsRegistryListener {

    private final Map<MetricName, ObjectName> registeredBeans;
    private final MBeanServer server;

    public static interface MetricMBean {
        public ObjectName objectName();
    }

    public static abstract class AbstractBean implements MetricMBean {
        private final ObjectName objectName;

        protected AbstractBean(ObjectName objectName) {
            this.objectName = objectName;
        }

        @Override
        public ObjectName objectName() {
            return objectName;
        }
    }

    public static interface GaugeMBean extends MetricMBean {
        public Object getValue();
    }

    public static class Gauge extends AbstractBean implements GaugeMBean {
        private final GaugeMetric<?> metric;

        public Gauge(GaugeMetric<?> metric, ObjectName objectName) {
            super(objectName);
            this.metric = metric;
        }

        @Override
        public Object getValue() {
            return metric.value();
        }
    }

    public static interface CounterMBean extends MetricMBean {
        public long getCount();
    }

    public static class Counter extends AbstractBean implements CounterMBean {
        private final CounterMetric metric;

        public Counter(CounterMetric metric, ObjectName objectName) {
            super(objectName);
            this.metric = metric;
        }

        @Override
        public long getCount() {
            return metric.count();
        }
    }

    public static interface MeterMBean extends MetricMBean {
        public long getCount();
        public String getEventType();
        public TimeUnit getRateUnit();
        public double getMeanRate();
        public double getOneMinuteRate();
        public double getFiveMinuteRate();
        public double getFifteenMinuteRate();
    }

    public static class Meter extends AbstractBean implements MeterMBean {
        private final Metered metric;

        public Meter(Metered metric, ObjectName objectName) {
            super(objectName);
            this.metric = metric;
        }

        @Override
        public long getCount() {
            return metric.count();
        }

        @Override
        public String getEventType() {
            return metric.eventType();
        }

        @Override
        public TimeUnit getRateUnit() {
            return metric.rateUnit();
        }

        @Override
        public double getMeanRate() {
            return metric.meanRate();
        }

        @Override
        public double getOneMinuteRate() {
            return metric.oneMinuteRate();
        }

        @Override
        public double getFiveMinuteRate() {
            return metric.fiveMinuteRate();
        }

        @Override
        public double getFifteenMinuteRate() {
            return metric.fifteenMinuteRate();
        }
    }

    public static interface HistogramMBean extends MetricMBean {
        public long getCount();
        public double getMin();
        public double getMax();
        public double getMean();
        public double getStdDev();
        public double get50thPercentile();
        public double get75thPercentile();
        public double get95thPercentile();
        public double get98thPercentile();
        public double get99thPercentile();
        public double get999thPercentile();
        public List<?> values();
    }

    public static class Histogram implements HistogramMBean {
        private final ObjectName objectName;
        private final HistogramMetric metric;

        public Histogram(HistogramMetric metric, ObjectName objectName) {
            this.metric = metric;
            this.objectName = objectName;
        }

        @Override
        public ObjectName objectName() {
            return objectName;
        }

        @Override
        public double get50thPercentile() {
            return metric.percentiles(0.5)[0];
        }

        @Override
        public long getCount() {
            return metric.count();
        }

        @Override
        public double getMin() {
            return metric.min();
        }

        @Override
        public double getMax() {
            return metric.max();
        }

        @Override
        public double getMean() {
            return metric.mean();
        }

        @Override
        public double getStdDev() {
            return metric.stdDev();
        }

        @Override
        public double get75thPercentile() {
            return metric.percentiles(0.75)[0];
        }

        @Override
        public double get95thPercentile() {
            return metric.percentiles(0.95)[0];
        }

        @Override
        public double get98thPercentile() {
            return metric.percentiles(0.98)[0];
        }

        @Override
        public double get99thPercentile() {
            return metric.percentiles(0.99)[0];
        }

        @Override
        public double get999thPercentile() {
            return metric.percentiles(0.999)[0];
        }

        @Override
        public List<?> values() {
            return metric.values();
        }
    }

    public static interface TimerMBean extends MeterMBean, HistogramMBean {
        public TimeUnit getLatencyUnit();
    }

    public static class Timer extends Meter implements TimerMBean {
        private final TimerMetric metric;

        public Timer(TimerMetric metric, ObjectName objectName) {
            super(metric, objectName);
            this.metric = metric;
        }

        @Override
        public double get50thPercentile() {
            return metric.percentiles(0.5)[0];
        }

        @Override
        public TimeUnit getLatencyUnit() {
            return metric.durationUnit();
        }

        @Override
        public double getMin() {
            return metric.min();
        }

        @Override
        public double getMax() {
            return metric.max();
        }

        @Override
        public double getMean() {
            return metric.mean();
        }

        @Override
        public double getStdDev() {
            return metric.stdDev();
        }

        @Override
        public double get75thPercentile() {
            return metric.percentiles(0.75)[0];
        }

        @Override
        public double get95thPercentile() {
            return metric.percentiles(0.95)[0];
        }

        @Override
        public double get98thPercentile() {
            return metric.percentiles(0.98)[0];
        }

        @Override
        public double get99thPercentile() {
            return metric.percentiles(0.99)[0];
        }

        @Override
        public double get999thPercentile() {
            return metric.percentiles(0.999)[0];
        }

        @Override
        public List<?> values() {
            return metric.values();
        }
    }

    private static JmxReporter INSTANCE;
    public static void startDefault(MetricsRegistry defaultMetricsRegistry) {
        INSTANCE = new JmxReporter(defaultMetricsRegistry);
        INSTANCE.start();
    }

    public static void shutdownDefault() {
        if (INSTANCE != null) {
            INSTANCE.shutdown();
        }
    }

    public JmxReporter(MetricsRegistry metricsRegistry) {
        super(metricsRegistry);
        this.registeredBeans = new HashMap<MetricName, ObjectName>();
        this.server = ManagementFactory.getPlatformMBeanServer();
    }

    @Override
    public void onMetricAdded(MetricName name, Metric metric) {
        if (metric != null) {
            try {
                final ObjectName objectName = new ObjectName(name.getMBeanName());
                if (metric instanceof GaugeMetric) {
                    registerBean(name, new Gauge((GaugeMetric<?>) metric, objectName), objectName);
                } else if (metric instanceof CounterMetric) {
                    registerBean(name, new Counter((CounterMetric) metric, objectName), objectName);
                } else if (metric instanceof HistogramMetric) {
                    registerBean(name, new Histogram((HistogramMetric) metric, objectName), objectName);
                } else if (metric instanceof MeterMetric) {
                    registerBean(name, new Meter((MeterMetric) metric, objectName), objectName);
                } else if (metric instanceof TimerMetric) {
                    registerBean(name, new Timer((TimerMetric) metric, objectName), objectName);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onMetricRemoved(MetricName name) {
        ObjectName objectName = registeredBeans.remove(name);
        if (objectName != null) {
            try {
                server.unregisterMBean(objectName);
            } catch (Exception ignored) {
            }
        }
    }

    public final void start() {
        metricsRegistry.addListener(this);
    }

    private void registerBean(MetricName name, MetricMBean bean, ObjectName objectName) throws MBeanRegistrationException, InstanceAlreadyExistsException, NotCompliantMBeanException {
        server.registerMBean(bean, objectName);
        registeredBeans.put(name, objectName);
    }

    @Override
    public void shutdown() {
        metricsRegistry.removeListener(this);
        for (ObjectName name : registeredBeans.values()) {
            try {
                server.unregisterMBean(name);
            } catch (Exception ignored) {

            }
        }
        registeredBeans.clear();
    }

}
