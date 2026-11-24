import type { Response } from 'express';
import type { Environment } from './types.js';

interface Client {
  id: number;
  projectId: string;
  environment: Environment;
  device?: string;
  res: Response;
}

/**
 * Server-Sent Events hub for live preview.
 *
 * Devices subscribe per project+environment; publishing a screen pushes the new
 * definition to exactly the devices watching that environment, so a designer
 * previewing on `dev` never disturbs anyone on `prod`. Optionally a device token
 * narrows delivery further, which is how "preview on *my* phone" works while
 * teammates keep seeing published content.
 */
class PreviewHub {
  private clients = new Map<number, Client>();
  private nextId = 1;

  subscribe(projectId: string, environment: Environment, device: string | undefined, res: Response): () => void {
    const id = this.nextId++;
    this.clients.set(id, { id, projectId, environment, device, res });

    res.write(`: connected ${id}\n\n`);
    // Comment frames keep proxies from idling the connection out.
    const heartbeat = setInterval(() => {
      res.write(`: ping ${Date.now()}\n\n`);
    }, 20_000);

    return () => {
      clearInterval(heartbeat);
      this.clients.delete(id);
    };
  }

  broadcast(
    projectId: string,
    environment: Environment,
    event: string,
    payload: unknown,
    device?: string,
  ): number {
    const body = `event: ${event}\ndata: ${JSON.stringify(payload)}\n\n`;
    let delivered = 0;
    for (const client of this.clients.values()) {
      if (client.projectId !== projectId) continue;
      if (client.environment !== environment) continue;
      // A device-targeted push goes only to that device; a general publish goes
      // to everyone on the environment, targeted devices included.
      if (device && client.device !== device) continue;
      client.res.write(body);
      delivered++;
    }
    return delivered;
  }

  stats(): { total: number; byProject: Record<string, number> } {
    const byProject: Record<string, number> = {};
    for (const client of this.clients.values()) {
      const key = `${client.projectId}/${client.environment}`;
      byProject[key] = (byProject[key] ?? 0) + 1;
    }
    return { total: this.clients.size, byProject };
  }
}

export const previewHub = new PreviewHub();
