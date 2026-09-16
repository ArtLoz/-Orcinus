#include "android_log_sink.hpp"

#include <android/log.h>

#include <mutex>
#include <string>

#include <boost/log/core.hpp>
#include <boost/log/sinks/basic_sink_backend.hpp>
#include <boost/log/sinks/sync_frontend.hpp>
#include <boost/log/trivial.hpp>
#include <boost/smart_ptr/make_shared.hpp>

namespace orcinus::orca {
namespace {

class LogcatBackend final
    : public boost::log::sinks::basic_formatted_sink_backend<char, boost::log::sinks::synchronized_feeding> {
public:
    static void consume(const boost::log::record_view& record, const std::string& message)
    {
        __android_log_write(priority(record), "OrcaSlicer", message.c_str());
    }

private:
    static int priority(const boost::log::record_view& record)
    {
        const auto severity = record[boost::log::trivial::severity];
        if (!severity) {
            return ANDROID_LOG_INFO;
        }
        switch (*severity) {
        case boost::log::trivial::trace:
            return ANDROID_LOG_VERBOSE;
        case boost::log::trivial::debug:
            return ANDROID_LOG_DEBUG;
        case boost::log::trivial::info:
            return ANDROID_LOG_INFO;
        case boost::log::trivial::warning:
            return ANDROID_LOG_WARN;
        case boost::log::trivial::error:
            return ANDROID_LOG_ERROR;
        case boost::log::trivial::fatal:
            return ANDROID_LOG_FATAL;
        }
        return ANDROID_LOG_INFO;
    }
};

}  // namespace

void install_android_log_sink()
{
    static std::once_flag installed;
    std::call_once(installed, [] {
        boost::log::core::get()->add_sink(
            boost::make_shared<boost::log::sinks::synchronous_sink<LogcatBackend>>()
        );
    });
}

}  // namespace orcinus::orca
