using InteriorAI.Data;
using InteriorAI.Data.Seed;
using InteriorAI.Services;
using Microsoft.EntityFrameworkCore;
using Microsoft.OpenApi.Models;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseSqlServer(builder.Configuration.GetConnectionString("DefaultConnection")));

builder.Services.AddScoped<AuthManager>();
builder.Services.AddScoped<DesignManager>();
builder.Services.AddScoped<DesignStudioSeeder>();
builder.Services.AddScoped<IDesignPromptService, DesignPromptService>();
builder.Services.AddControllers();
builder.Services.AddHttpClient<IImageStorageService, ImgBBImageStorageService>();
builder.Services.AddHttpClient<IImageGenerationService, NanoBananaImageGenerationService>();
builder.Services.AddEndpointsApiExplorer();

builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new OpenApiInfo { Title = "InteriorAI API", Version = "v1" });


    c.AddSecurityDefinition("user-id", new OpenApiSecurityScheme
    {
        In = ParameterLocation.Header,
        Name = "user-id",
        Type = SecuritySchemeType.ApiKey,
        Description = "Vui lòng nhập User ID"
    });

    c.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        {
            new OpenApiSecurityScheme
            {
                Reference = new OpenApiReference
                {
                    Type = ReferenceType.SecurityScheme,
                    Id = "user-id"
                }
            },
            Array.Empty<string>()
        }
    });
});

var app = builder.Build();

using (var scope = app.Services.CreateScope())
{
    var seeder = scope.ServiceProvider.GetRequiredService<DesignStudioSeeder>();
    await seeder.EnsureSeedDataAsync();
}

// Configure the HTTP request pipeline.
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

if (!app.Environment.IsDevelopment())
{
    app.UseHttpsRedirection();
}


app.UseStaticFiles();

app.MapControllers();

app.Run();

record WeatherForecast(DateOnly Date, int TemperatureC, string? Summary)
{
    public int TemperatureF => 32 + (int)(TemperatureC / 0.5556);
}

