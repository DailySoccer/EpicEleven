package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.google.common.collect.ImmutableMap;
import model.*;
import org.bson.types.ObjectId;
import play.data.Form;
import play.data.validation.Constraints;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

import java.util.List;

import static play.data.Form.form;

@AllowCors.Origin
public class GuildController extends Controller {

    /*
     * Devuelve la lista de Guilds
     */
    public static Result getGuilds() {
        return new ReturnHelper(ImmutableMap.of(
                "guilds", Guild.findAll()
        )).toResult(JsonViews.Public.class);
    }

    public static class CreateGuildParams {
        @Constraints.Required
        public String name;
    }

    @UserAuthenticated
    public static Result createGuild() {
        Form<CreateGuildParams> createGuildForm = form(CreateGuildParams.class).bindFromRequest();

        User theUser = (User) ctx().args.get("User");

        if (!createGuildForm.hasErrors()) {
            CreateGuildParams params = createGuildForm.get();

            // Creamos el Guild (el user será ADMIN)
            Guild guild = new Guild(theUser.userId, params.name);
            guild.insert();

            // Asociamos al usuario con el Guild
            theUser.setGuild(guild.guildId);
        }

        Object result = createGuildForm.errorsAsJson();
        return new ReturnHelper(!createGuildForm.hasErrors(), result).toResult();
    }

    /*
        Solicitud para entrar en el Guild
     */
    @UserAuthenticated
    public static Result requestToEnter(String guildId) {
        User theUser = (User)ctx().args.get("User");

        // 1. Encontrar el Guild
        Guild guild = Guild.findOne(new ObjectId(guildId));
        if (guild != null) {

            // 2. Solicitud de entrar en el Guild
            guild.request(theUser.userId);
        }

        return new ReturnHelper(ImmutableMap.of(
                "ok", true
        )).toResult();
    }

    /*
        Rechazar la entrada en el Guild
     */
    @UserAuthenticated
    public static Result rejectRequestToEnter(String userId) {
        User theUser = (User)ctx().args.get("User");

        // 1. Que el usuario tenga permisos de administracion del Guild
        Guild guild = Guild.findOne(theUser.guildId);
        if ((guild != null) && guild.hasRol(theUser.userId, Guild.UserRol.ADMIN)) {

            User newMember = User.findOne(userId);

            // 2. Que el usuario haya solicitado su ingreso en el Guild
            if (guild.hasRequested(newMember.userId)) {

                // Eliminar la solicitud de ingreso
                guild.rejectRequest(newMember);
            }
        }

        return new ReturnHelper(ImmutableMap.of(
                "ok", true
        )).toResult();
    }

    /*
        Aceptar un nuevo miembro en el Guild
     */
    @UserAuthenticated
    public static Result acceptMember(String newMemberId) {
        User theUser = (User)ctx().args.get("User");

        // 1. Que el usuario tenga permisos de administracion del Guild
        Guild guild = Guild.findOne(theUser.guildId);
        if ((guild != null) && guild.hasRol(theUser.userId, Guild.UserRol.ADMIN)) {

            // 2. Que el nuevo member no esté en el Guild
            User newMember = User.findOne(newMemberId);
            if ((newMember.guildId == null) || !guild.guildId.equals(newMember.guildId)) {

                // 3. Que el nuevo member haya solicitado su ingreso en el Guild
                if (guild.hasRequested(newMember.userId)) {

                    // Cambiamos el Guild
                    guild.acceptRequest(newMember);
                }
            }
        }

        return new ReturnHelper(ImmutableMap.of(
                "ok", true
        )).toResult();
    }

    /*
        Cambiar el Rol de un miembro (puede ser el propio usuario si tiene el rol adecuado)
     */
    @UserAuthenticated
    public static Result changeRol(String memberId, String rol) {
        User theUser = (User)ctx().args.get("User");

        // 1. Que el usuario tenga permisos de administración en el Guild del que es miembro
        Guild guild = Guild.findOne(theUser.guildId);
        if ((guild != null) && guild.hasRol(theUser.userId, Guild.UserRol.ADMIN)) {

            // 2. Que el usuario esté en el Guild
            User member = User.findOne(memberId);
            if (guild.guildId.equals(member.guildId)) {

                // TODO: Cambiar el rol
            }
        }

        return new ReturnHelper(ImmutableMap.of(
                "ok", true
        )).toResult();
    }

    /*
        Eliminar a un miembro del Guild
     */
    @UserAuthenticated
    public static Result removeMember(String memberId) {
        User theUser = (User)ctx().args.get("User");

        // 1. Que el usuario tenga permisos de administracion del Guild
        Guild guild = Guild.findOne(theUser.guildId);
        if ((guild != null) && guild.hasRol(theUser.userId, Guild.UserRol.ADMIN)) {

            // 2. Que el member esté en ese mismo Guild
            User member = User.findOne(memberId);
            if (guild.guildId.equals(member.guildId)) {

                // Quitar del Guild
                guild.removeMember(member);
            }
        }

        return new ReturnHelper(ImmutableMap.of(
                "ok", true
        )).toResult();
    }

    /*
        Quitarse de un Guild (un usuario decide salirse del Guild al que pertenece)
     */
    @UserAuthenticated
    public static Result removeFromGuild() {
        User theUser = (User)ctx().args.get("User");

        Guild guild = Guild.findOne(theUser.guildId);
        if (guild != null) {
            guild.removeMember(theUser);
        }

        return new ReturnHelper(ImmutableMap.of(
                "ok", true
        )).toResult();
    }

    /*
        Devolver el leaderboard del Guild al que pertenece el usuario
     */
    @UserAuthenticated
    public static Result getLeaderboard() {
        User theUser = (User)ctx().args.get("User");

        return new ReturnHelper(ImmutableMap.of("users", UserInfo.findGuildWithAchievements(theUser.guildId))).toResult();
    }
}
