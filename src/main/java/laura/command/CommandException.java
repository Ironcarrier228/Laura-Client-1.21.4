package laura.command;

import laura.discord.DiscordIPCException;

import laura.discord.RpcErrorCode;

public class CommandException extends DiscordIPCException {
    private final RpcErrorCode errorCode;

    public CommandException(RpcErrorCode errorCode, String message) {
        super("RPC error " + errorCode.a() + " (" + errorCode.name() + "): " + message);
        this.errorCode = errorCode;
    }

    public RpcErrorCode a() {
        return this.errorCode;
    }
}
