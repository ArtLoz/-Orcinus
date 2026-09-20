#include "engine_context.hpp"

#include "libslic3r/AppConfig.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/Thread.hpp"

namespace orcinus::orca::detail {

EngineContext& engine()
{
    static EngineContext context;
    return context;
}

void follow_config(EngineContext& context)
{
    if (!context.bundle_follows_config) {
        context.bundle->load_selections(*context.config);
        context.bundle_follows_config = true;
    }
}

Slic3r::DynamicPrintConfig model_config(const ModelSettings& settings)
{
    Slic3r::DynamicPrintConfig config;
    Slic3r::ConfigSubstitutionContext substitutions(Slic3r::ForwardCompatibilitySubstitutionRule::Enable);
    for (std::size_t i = 0; i < settings.keys.size() && i < settings.values.size(); ++i) {
        if (Slic3r::print_config_def.get(settings.keys[i]) == nullptr) {
            continue;
        }
        config.set_deserialize(settings.keys[i], settings.values[i], substitutions);
    }
    return config;
}

void save_config(EngineContext& context)
{
    Slic3r::save_main_thread_id();
    context.config->save();
}

}  // namespace orcinus::orca::detail
